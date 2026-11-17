import FirebaseMessaging
import Shared
import UIKit

/// This iPhone's Firebase Messaging token for the shared code (iPhone phase 4 part C): the shared
/// `IosPushToken`, over `Messaging.messaging()`, as `GoogleSignInBridge` is Google's sheet. The shared
/// `IosPushRegistration` keeps the token on the signed-in profile (`users/{uid}.fcmToken`) while
/// pushes are on, and detaches it at sign-out and account deletion.
///
/// Apple's device token reaches Firebase Messaging through `AppDelegate`
/// (`didRegisterForRemoteNotificationsWithDeviceToken`), with Firebase's app-delegate swizzling off
/// (`FirebaseAppDelegateProxyEnabled` in `Info.plist`), so nothing happens behind the app's back.
///
/// On the Firebase emulators (`enabled` false: CI and a build without `GoogleService-Info.plist`)
/// Firebase Messaging is never touched: it would ask Apple and Google for a token under the
/// emulators' fake configuration. The token is then nil, and the push switch says so.
final class PushTokenBridge: NSObject, IosPushToken, MessagingDelegate {
    /// Whether the app talks to the real project, where Firebase Messaging can register.
    let enabled: Bool

    init(enabled: Bool) {
        self.enabled = enabled
        super.init()
    }

    /// Starts Firebase Messaging after the shared code has configured Firebase (`IosApp.start`):
    /// this bridge is its delegate, and it fetches a token on its own from now on
    /// (`FirebaseMessagingAutoInitEnabled` is off in `Info.plist` until this runs).
    func start() {
        guard enabled else { return }
        Messaging.messaging().delegate = self
        Messaging.messaging().isAutoInitEnabled = true
    }

    /// Hands Apple's device token to Firebase Messaging, which exchanges it for the FCM token.
    func setApnsToken(_ deviceToken: Data) {
        guard enabled else { return }
        Messaging.messaging().apnsToken = deviceToken
    }

    func current(completion: @escaping @Sendable (String?, String?) -> Void) {
        guard enabled else {
            completion(nil, "Firebase Messaging is not used on the emulators")
            return
        }
        // The next registration: the shared code asks only while the token would be kept.
        Messaging.messaging().isAutoInitEnabled = true
        Messaging.messaging().token { token, error in
            if let error {
                completion(nil, Self.describe(error))
            } else {
                completion(token, token == nil ? "Firebase Messaging answered no token" : nil)
            }
        }
    }

    func delete(completion: @escaping @Sendable (String?) -> Void) {
        guard enabled else {
            completion(nil)
            return
        }
        // No fresh token on its own until the next registration (`current`): a sign-out or a
        // deletion detaches first and ends the session last. The shared code refuses to keep one
        // for the departing account either way (`IosPushRegistration`).
        Messaging.messaging().isAutoInitEnabled = false
        Messaging.messaging().deleteToken { error in
            completion(error.map(Self.describe))
        }
    }

    /// A new or refreshed token: the shared code keeps it on the profile, as Android's `onNewToken`.
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let fcmToken, !fcmToken.isEmpty else { return }
        DispatchQueue.main.async {
            IosApp.shared.pushTokenChanged(token: fcmToken)
        }
    }

    /// The error's domain and code, which is what the log needs (never a token).
    private static func describe(_ error: Error) -> String {
        let ns = error as NSError
        return "\(ns.domain) \(ns.code)"
    }
}
