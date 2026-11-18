import Foundation
import Shared
import WidgetKit

/// Asks the home screen to draw the Today widget again (iPhone phase 4 part D): the shared
/// `WidgetReload`, over WidgetKit, as `PushTokenBridge` is Firebase Messaging. The shared
/// `WidgetSnapshotWriter` calls it once it has written a snapshot that differs from the stored one
/// (`<App Group container>/widget/today.json`); an unchanged snapshot asks for nothing.
final class WidgetReloadBridge: NSObject, WidgetReload {
    func reloadAll() {
        WidgetCenter.shared.reloadAllTimelines()
    }
}
