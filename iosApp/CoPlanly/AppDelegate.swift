import Shared
import UIKit

/// The iPhone app's entry point (MON-24 phase 3). It reads what the build hands the shared code and
/// starts it (`IosApp`, in the `Shared` framework `:shared-ui` builds), with Google's sign-in sheet
/// (`GoogleSignInBridge`). The window is the scene's
/// (`SceneDelegate`, named in `Info.plist`'s scene manifest), which shows the shared code's root
/// view controller. Every screen is the shared code's; nothing the user reads is written here.
@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
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
        do {
            try IosApp.shared.start(config: config, google: GoogleSignInBridge())
        } catch {
            fatalError("The shared code did not start: \(error)")
        }
        return true
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
