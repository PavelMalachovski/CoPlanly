import Shared
import UIKit

/// The app's one window (MON-24 phase 3), in the scene life cycle iOS asks apps to adopt: the
/// window belongs to its scene, and its root is the shared code's native shell (`IosApp`), which
/// `AppDelegate` has started before any scene connects.
final class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        guard let windowScene = scene as? UIWindowScene else { return }
        let window = UIWindow(windowScene: windowScene)
        window.rootViewController = IosApp.shared.rootViewController()
        window.makeKeyAndVisible()
        self.window = window
        // A link the app was launched with arrives here rather than in `openURLContexts`.
        open(connectionOptions.urlContexts)
    }

    /// A link opened while the app runs.
    func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        open(URLContexts)
    }

    /// Hands each URL to Google's sign-in first, which its sheet returns through
    /// (`GoogleSignInBridge`), and to the app's own `coplanly://` links (`IosApp.openUrl`) when
    /// Google did not take it.
    private func open(_ contexts: Set<UIOpenURLContext>) {
        for context in contexts {
            if GoogleSignInBridge.handle(context.url) { continue }
            _ = IosApp.shared.openUrl(url: context.url.absoluteString)
        }
    }
}
