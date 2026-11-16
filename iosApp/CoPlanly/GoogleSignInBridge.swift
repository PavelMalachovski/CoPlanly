import GoogleSignIn
import Shared
import UIKit

/// Google's sign-in sheet for the shared sign-in screen (MON-24 phase 3, Task 9): the shared code's
/// `GoogleIdTokenSource`, over Google Sign-In for iOS. The shared `IosCredentialSignIn` turns its
/// answer into the result `AuthViewModel` folds, and holds the access token for the Firebase sign-in.
///
/// It is available only when the app carries a `GoogleService-Info.plist` with a `CLIENT_ID` and the
/// build registered that file's `REVERSED_CLIENT_ID` as a URL scheme (the "Register the app's URL
/// schemes" build phase, iosApp/README.md). Google Sign-In stops the app when it is asked to sign in
/// without that scheme, so it is never asked then: the sign-in screen offers e-mail alone, as it does
/// in CI and on the emulators, where no such file is bundled.
///
/// The same sheet connects Google Calendar from Settings (MON-24 phase 4, part A, Task 7,
/// `requestCalendarAccess`): it asks for the read-only Calendar scope the shared code names and answers
/// the `serverAuthCode` the server redeems. That needs the web client Google issues the code for, the
/// `GIDServerClientID` of `Info.plist` (the build setting `COPLANLY_GOOGLE_WEB_CLIENT_ID`); blank, as in
/// CI, Google Calendar is not offered.
///
/// Nothing the parent reads is written here: the sheet is Google's, and every other word on the
/// screen is the shared code's. The `error` text goes to the log only.
final class GoogleSignInBridge: NSObject, GoogleIdTokenSource {
    private let clientId: String?
    private let serverClientId: String?

    /// Reads Google's configuration from `bundle` and hands Google Sign-In its client id.
    init(bundle: Bundle = .main) {
        clientId = Self.registeredClientId(in: bundle)
        serverClientId = Self.nonBlank(bundle.object(forInfoDictionaryKey: "GIDServerClientID") as? String)
        super.init()
        if let clientId {
            GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientId)
        }
    }

    var available: Bool { clientId != nil }

    /// Opens Google's sheet. The shared code calls it on the main thread, where UIKit and Google
    /// Sign-In must be used, and Google Sign-In answers there too.
    func requestIdToken(
        completion: @escaping @Sendable (String?, String?, KotlinBoolean, String?) -> Void
    ) {
        MainActor.assumeIsolated {
            signIn(completion: completion)
        }
    }

    @MainActor
    private func signIn(completion: @escaping @Sendable (String?, String?, KotlinBoolean, String?) -> Void) {
        guard clientId != nil, let presenter = Self.topViewController() else {
            completion(nil, nil, KotlinBoolean(value: false), "Google sign-in has no client id or no screen to show on")
            return
        }
        GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
            if let error {
                let cancelled = (error as? GIDSignInError)?.code == .canceled
                completion(nil, nil, KotlinBoolean(value: cancelled), cancelled ? nil : String(describing: error))
                return
            }
            guard let user = result?.user, let idToken = user.idToken?.tokenString else {
                completion(nil, nil, KotlinBoolean(value: false), "Google sign-in answered no ID token")
                return
            }
            completion(idToken, user.accessToken.tokenString, KotlinBoolean(value: false), nil)
        }
    }

    var calendarAvailable: Bool { clientId != nil && serverClientId != nil }

    /// Opens Google's sheet asking for `scope` beside the sign-in, with the web client as the server
    /// client id, and answers the `serverAuthCode`. The configuration the app sign-in uses (no server
    /// client id) is put back as soon as the sheet answers, so its ID token is issued as before; the
    /// shared code calls it on the main thread, where Google Sign-In answers too.
    func requestCalendarAccess(
        scope: String,
        completion: @escaping @Sendable (String?, String?, String?, KotlinBoolean, String?) -> Void
    ) {
        MainActor.assumeIsolated {
            connectCalendar(scope: scope, completion: completion)
        }
    }

    @MainActor
    private func connectCalendar(
        scope: String,
        completion: @escaping @Sendable (String?, String?, String?, KotlinBoolean, String?) -> Void
    ) {
        guard let clientId, let serverClientId, let presenter = Self.topViewController() else {
            completion(nil, nil, nil, KotlinBoolean(value: false), "Google Calendar has no web client id or no screen to show on")
            return
        }
        let signIn = GIDSignIn.sharedInstance
        let previous = signIn.configuration
        signIn.configuration = GIDConfiguration(clientID: clientId, serverClientID: serverClientId)
        signIn.signIn(withPresenting: presenter, hint: nil, additionalScopes: [scope]) { result, error in
            signIn.configuration = previous
            if let error {
                let cancelled = (error as? GIDSignInError)?.code == .canceled
                completion(nil, nil, nil, KotlinBoolean(value: cancelled), cancelled ? nil : String(describing: error))
                return
            }
            guard let result, let code = result.serverAuthCode else {
                completion(nil, nil, nil, KotlinBoolean(value: false), nil)
                return
            }
            let profile = result.user.profile
            completion(code, profile?.email, profile?.name, KotlinBoolean(value: false), nil)
        }
    }

    /// Ends Google Sign-In's own session (the account it keeps in the Keychain and offers first), when
    /// the app signs out and on the first launch after an install, so a shared phone does not preselect
    /// the previous parent's account. Google Sign-In is used on the main thread; a call from elsewhere
    /// is handed to it. Nothing to end without a client id: the sheet never ran.
    func signOut() {
        guard clientId != nil else { return }
        if Thread.isMainThread {
            GIDSignIn.sharedInstance.signOut()
        } else {
            DispatchQueue.main.async { GIDSignIn.sharedInstance.signOut() }
        }
    }

    /// The URL Google's sheet returns through, handed on by the scene (`SceneDelegate`).
    @MainActor
    static func handle(_ url: URL) -> Bool {
        GIDSignIn.sharedInstance.handle(url)
    }

    /// The `CLIENT_ID` of the bundled `GoogleService-Info.plist`, when the app also registered its
    /// `REVERSED_CLIENT_ID` as a URL scheme; otherwise nil.
    private static func registeredClientId(in bundle: Bundle) -> String? {
        guard
            let path = bundle.path(forResource: "GoogleService-Info", ofType: "plist"),
            let google = NSDictionary(contentsOfFile: path),
            let clientId = nonBlank(google["CLIENT_ID"] as? String),
            let reversed = nonBlank(google["REVERSED_CLIENT_ID"] as? String)
        else {
            return nil
        }
        let urlTypes = bundle.object(forInfoDictionaryKey: "CFBundleURLTypes") as? [[String: Any]] ?? []
        let schemes = urlTypes.flatMap { $0["CFBundleURLSchemes"] as? [String] ?? [] }
        return schemes.contains(reversed) ? clientId : nil
    }

    /// The view controller on top of the app's key window, which presents Google's sheet.
    @MainActor
    private static func topViewController() -> UIViewController? {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }
        var top = window?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }

    private static func nonBlank(_ value: String?) -> String? {
        guard let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else {
            return nil
        }
        return trimmed
    }
}
