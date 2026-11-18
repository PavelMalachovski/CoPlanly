import Shared
import UIKit
import UserNotifications

/// The iPhone app's entry point (MON-24 phase 3). It reads what the build hands the shared code and
/// starts it (`IosApp`, in the `Shared` framework `:shared-ui` builds), with Google's sign-in sheet
/// (`GoogleSignInBridge`) and Apple's (`AppleSignInBridge`). The window is the scene's
/// (`SceneDelegate`, named in `Info.plist`'s scene manifest), which shows the shared code's root
/// view controller. Every screen is the shared code's; nothing the user reads is written here.
///
/// Pushes (iPhone phase 4 part C): the app registers with Apple's service and hands the device
/// token to Firebase Messaging (`PushTokenBridge`), whose token the shared code keeps on the profile.
/// It is the notification center's delegate: a push that arrives while the app is open shows as a
/// banner with its sound, and a tapped one hands its data to `IosApp.openPush`, which switches the
/// family it names before it opens the screen (CLAUDE.md item 15). The notification permission is
/// never asked here: the shared screens ask it in context (UX overhaul item 7).
///
/// The background refresh (iPhone phase 4 part D): the handler for `app.coplanly.refresh` is
/// registered before launching ends (`BackgroundRefreshBridge`), and runs the shared chain
/// (`IosApp.runBackgroundRefresh`), which ends at once while the phone is locked.
@main
final class AppDelegate: UIResponder, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    /// This iPhone's Firebase Messaging token, for the shared code.
    private var pushToken: PushTokenBridge?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        let config = IosAppConfig(
            emulatorHost: Self.emulatorHost(),
            versionName: Self.infoString("CFBundleShortVersionString") ?? "",
            privacyPolicyUrl: Self.infoString("CoPlanlyPrivacyPolicyURL"),
            termsUrl: Self.infoString("CoPlanlyTermsURL"),
            supportEmail: Self.infoString("CoPlanlySupportEmail")
        )
        let push = PushTokenBridge(enabled: config.emulatorHost == nil)
        pushToken = push
        do {
            try IosApp.shared.start(
                config: config,
                google: GoogleSignInBridge(),
                apple: AppleSignInBridge(),
                pushToken: push,
                widgetReload: WidgetReloadBridge(),
                background: BackgroundRefreshBridge()
            )
        } catch {
            fatalError("The shared code did not start: \(error)")
        }
        // The background refresh (phase 4 part D): iOS requires the handler before this method
        // returns. The shared code submits the request on entering the background.
        BackgroundRefreshBridge.register()
        // Before this method returns, so a tap that launched the app reaches the delegate.
        UNUserNotificationCenter.current().delegate = self
        // The push UI test only (PushUITests): it sets this variable to have the system's question
        // asked at once, which it answers, since no screen it walks to asks it and the simulator has
        // no command that grants it. Never set otherwise: the app asks in context (UX item 7). Debug
        // builds only (CI builds its UI tests in Debug), so a Release build has no such hook.
        #if DEBUG
        if ProcessInfo.processInfo.environment["COPLANLY_UITEST_ASK_NOTIFICATIONS"] == "1" {
            UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
        }
        #endif
        push.start()
        if push.enabled {
            // Asks for no permission: an APNs token is issued without one.
            application.registerForRemoteNotifications()
        }
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        pushToken?.setApnsToken(deviceToken)
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        let ns = error as NSError
        NSLog("CoPlanly could not register for pushes: %@ %ld", ns.domain, ns.code)
    }

    /// A push that arrives while the app is open: shown as a banner with its sound, as in the tray.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }

    /// A tapped push: its data, string keys and values only (the `aps` block is Apple's), to the
    /// shared code, which drops one for another account and switches the family before opening.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if response.actionIdentifier == UNNotificationDefaultActionIdentifier {
            IosApp.shared.openPush(data: Self.stringData(response.notification.request.content.userInfo))
        }
        completionHandler()
    }

    /// The push's data as the shared code reads it: every string key with a string value.
    private static func stringData(_ userInfo: [AnyHashable: Any]) -> [String: String] {
        var data: [String: String] = [:]
        for (key, value) in userInfo {
            if let key = key as? String, let value = value as? String {
                data[key] = value
            }
        }
        return data
    }

    /// Where the Firebase emulators run, or nil for the real project.
    ///
    /// In order: the `COPLANLY_EMULATOR_HOST` environment variable (a scheme or `simctl launch`
    /// sets it), the `CoPlanlyEmulatorHost` key of `Info.plist` (the build setting of the same
    /// name), and then `127.0.0.1` whenever no `GoogleService-Info.plist` is bundled, so a build
    /// without the real configuration never reaches production.
    private static func emulatorHost() -> String? {
        if let host = nonBlank(ProcessInfo.processInfo.environment["COPLANLY_EMULATOR_HOST"]) {
            return host
        }
        if let host = infoString("CoPlanlyEmulatorHost") {
            return host
        }
        if Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") == nil {
            return "127.0.0.1"
        }
        return nil
    }

    /// A string from the app's `Info.plist`, or nil when it is missing or blank.
    private static func infoString(_ key: String) -> String? {
        nonBlank(Bundle.main.object(forInfoDictionaryKey: key) as? String)
    }

    private static func nonBlank(_ value: String?) -> String? {
        guard let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else {
            return nil
        }
        return trimmed
    }
}
