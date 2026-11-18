import BackgroundTasks
import Foundation
import Shared

/// The background refresh's scheduling (iPhone phase 4 part D): the shared `BackgroundScheduling`
/// over `BGTaskScheduler`, as `WidgetReloadBridge` is WidgetKit. One request for
/// `app.coplanly.refresh` (`Info.plist`'s `BGTaskSchedulerPermittedIdentifiers`), never before 30
/// minutes from now; iOS decides when, and may never run it. The shared code submits it on entering
/// the background and at the end of each run while somebody is signed in, and cancels it when
/// nobody is (`IosApp`).
final class BackgroundRefreshBridge: NSObject, BackgroundScheduling {
    /// The task's identifier, which `AppDelegate` registers its handler for.
    static let identifier = "app.coplanly.refresh"

    /// The least time between two runs the app asks for.
    private static let interval: TimeInterval = 30 * 60

    func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: Self.identifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: Self.interval)
        do {
            // Replaces a pending request of the same identifier.
            try BGTaskScheduler.shared.submit(request)
        } catch {
            // Refused on the simulator, with Background App Refresh off, or past the pending limit:
            // nothing depends on the refresh running.
            let ns = error as NSError
            NSLog("CoPlanly could not schedule the background refresh: %@ %ld", ns.domain, ns.code)
        }
    }

    func cancel() {
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: Self.identifier)
    }

    /// Registers the task's handler, after `IosApp.start`: iOS requires it before `didFinishLaunching`
    /// returns. A run before the shared code started reports no success (`runBackgroundRefresh`).
    static func register() {
        let registered = BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: nil) { task in
            guard let refresh = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            run(refresh)
        }
        if !registered {
            NSLog("CoPlanly could not register the background refresh")
        }
    }

    /// Hands [task] to the shared chain (`IosApp.runBackgroundRefresh`), whose completion is called
    /// once, and lets the expiration handler cancel it, which completes the task with no success.
    private static func run(_ task: BGAppRefreshTask) {
        let run = IosApp.shared.runBackgroundRefresh { success in
            task.setTaskCompleted(success: success.boolValue)
        }
        task.expirationHandler = {
            run.cancel()
        }
    }
}
