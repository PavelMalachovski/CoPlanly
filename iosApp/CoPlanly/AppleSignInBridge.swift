import AuthenticationServices
import Shared
import UIKit

/// Apple's sign-in sheet for the shared sign-in screen and the sign-in methods (MON-24 phase 4, part
/// A): the shared code's `AppleIdSource`, over AuthenticationServices, as `GoogleSignInBridge` is its
/// `GoogleIdTokenSource`. The shared `IosCredentialSignIn` makes the nonce, hands its SHA-256 here,
/// and turns the answer into a Firebase sign-in or link and the server's code exchange.
///
/// It asks for the full name and the e-mail address. Apple shares the name only at the first
/// authorisation, and only when the parent agrees; the address may be Apple's private relay.
///
/// The app needs the `com.apple.developer.applesignin` entitlement (`CoPlanly.entitlements`) for the
/// sheet to answer; whether the app offers Apple at all is the bundle's `CoplanlyAppleSignIn` flag,
/// which the shared code reads.
///
/// Nothing the parent reads is written here: the sheet is the system's. The `error` text goes to the
/// log only.
final class AppleSignInBridge: NSObject, AppleIdSource {
    /// The request on screen, kept alive until it answers: the controller holds its delegate weakly.
    private var pending: AppleIdRequest?

    /// The system offers the sheet on every iPhone the app supports (iOS 13 and later).
    var available: Bool { true }

    /// Opens Apple's sheet. The shared code calls it on the main thread, where UIKit and
    /// AuthenticationServices must be used; the controller answers there too.
    func requestAppleId(
        hashedNonce: String,
        completion: @escaping @Sendable (String?, String?, String?, String?, KotlinBoolean, String?) -> Void
    ) {
        MainActor.assumeIsolated {
            start(hashedNonce: hashedNonce, completion: completion)
        }
    }

    @MainActor
    private func start(
        hashedNonce: String,
        completion: @escaping @Sendable (String?, String?, String?, String?, KotlinBoolean, String?) -> Void
    ) {
        guard pending == nil else {
            // One sheet at a time: a second request is a cancel, as the system's own answer would be.
            completion(nil, nil, nil, nil, KotlinBoolean(value: true), nil)
            return
        }
        guard let anchor = Self.presentationAnchor() else {
            completion(nil, nil, nil, nil, KotlinBoolean(value: false), "Sign in with Apple has no window to show on")
            return
        }
        let provider = ASAuthorizationAppleIDProvider()
        let request = provider.createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = hashedNonce

        let controller = ASAuthorizationController(authorizationRequests: [request])
        let delegate = AppleIdRequest(anchor: anchor) { [weak self] idToken, code, given, family, cancelled, error in
            self?.pending = nil
            completion(idToken, code, given, family, KotlinBoolean(value: cancelled), error)
        }
        pending = delegate
        controller.delegate = delegate
        controller.presentationContextProvider = delegate
        controller.performRequests()
    }

    /// The window of the view controller on top of the app's key window, which the sheet is shown
    /// over, as `GoogleSignInBridge` presents on that controller.
    @MainActor
    private static func presentationAnchor() -> ASPresentationAnchor? {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }
        var top = window?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top?.view.window ?? window
    }
}

/// One Apple ID request: the controller's delegate and presentation context, which turns the
/// controller's answer into the bridge's completion, called once.
private final class AppleIdRequest: NSObject, ASAuthorizationControllerDelegate,
    ASAuthorizationControllerPresentationContextProviding {
    typealias Finish = (String?, String?, String?, String?, Bool, String?) -> Void

    private let anchor: ASPresentationAnchor
    private var finish: Finish?

    init(anchor: ASPresentationAnchor, finish: @escaping Finish) {
        self.anchor = anchor
        self.finish = finish
        super.init()
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        anchor
    }

    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization authorization: ASAuthorization
    ) {
        guard
            let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
            let tokenData = credential.identityToken,
            let idToken = String(data: tokenData, encoding: .utf8)
        else {
            answer(nil, nil, nil, nil, false, "Sign in with Apple answered no identity token")
            return
        }
        let code = credential.authorizationCode.flatMap { String(data: $0, encoding: .utf8) }
        answer(
            idToken,
            code,
            Self.nonBlank(credential.fullName?.givenName),
            Self.nonBlank(credential.fullName?.familyName),
            false,
            nil
        )
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        let cancelled = (error as? ASAuthorizationError)?.code == .canceled
        answer(nil, nil, nil, nil, cancelled, cancelled ? nil : "Sign in with Apple failed: \(Self.describe(error))")
    }

    /// Calls the completion once; a second answer from the controller is dropped.
    private func answer(
        _ idToken: String?, _ code: String?, _ given: String?, _ family: String?, _ cancelled: Bool, _ error: String?
    ) {
        guard let finish else { return }
        self.finish = nil
        finish(idToken, code, given, family, cancelled, error)
    }

    /// The error's domain and code, which is what the log needs.
    private static func describe(_ error: Error) -> String {
        let ns = error as NSError
        return "\(ns.domain) \(ns.code)"
    }

    private static func nonBlank(_ value: String?) -> String? {
        guard let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else {
            return nil
        }
        return trimmed
    }
}
