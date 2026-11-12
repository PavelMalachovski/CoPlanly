import GoogleSignIn
import Shared
import UIKit

/// Google's sign-in sheet for the shared sign-in screen (MON-24 phase 3, Task 9): the shared code's
/// `GoogleIdTokenSource`, over Google Sign-In for iOS. The shared `IosCredentialSignIn` turns its
/// answer into the result `AuthViewModel` folds, and holds the access token for the Firebase sign-in.
///
/// It is available only when the app carries a `GoogleService-Info.plist` with a `CLIENT_ID` and the
/// build registered that file's `REVERSED_CLIENT_ID` as a URL scheme (the "Register Google's URL
/// scheme" build phase, iosApp/README.md). Google Sign-In stops the app when it is asked to sign in
/// without that scheme, so it is never asked then: the sign-in screen offers e-mail alone, as it does
/// in CI and on the emulators, where no such file is bundled.
///
/// Nothing the parent reads is written here: the sheet is Google's, and every other word on the
/// screen is the shared code's. The `error` text goes to the log only.
final class GoogleSignInBridge: NSObject, GoogleIdTokenSource {
    private let clientId: String?

    /// Reads Google's configuration from `bundle` and hands Google Sign-In its client id.
    init(bundle: Bundle = .main) {
        clientId = Self.registeredClientId(in: bundle)
        super.init()
        if let clientId {
            GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientId)
        }
    }

    var available: Bool { clientId != nil }

    func requestIdToken(
        completion: @escaping (String?, String?, KotlinBoolean, String?) -> KotlinUnit
    ) {
        guard clientId != nil, let presenter = Self.topViewController() else {
            _ = completion(nil, nil, KotlinBoolean(value: false), "Google sign-in has no client id or no screen to show on")
            return
        }
        GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
            if let error {
                let cancelled = (error as? GIDSignInError)?.code == .canceled
                _ = completion(nil, nil, KotlinBoolean(value: cancelled), cancelled ? nil : String(describing: error))
                return
            }
            guard let user = result?.user, let idToken = user.idToken?.tokenString else {
                _ = completion(nil, nil, KotlinBoolean(value: false), "Google sign-in answered no ID token")
                return
            }
            _ = completion(idToken, user.accessToken.tokenString, KotlinBoolean(value: false), nil)
        }
    }

    /// The URL Google's sheet returns through, handed on by the scene (`SceneDelegate`).
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
