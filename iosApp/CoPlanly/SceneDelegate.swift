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
    }

    /// Hands Google's sign-in the URL its sheet returns through (`GoogleSignInBridge`).
    func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        for context in URLContexts {
            _ = GoogleSignInBridge.handle(context.url)
        }
    }
}
