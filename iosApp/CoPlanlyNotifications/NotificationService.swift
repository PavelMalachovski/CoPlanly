import Darwin
import Foundation
import PushWording
import UserNotifications

/// The Notification Service Extension (iPhone phase 4 part C, design §7.2): every push reaches the
/// iPhone as the server's neutral alert (`CoPlanly` / `You have a new update in CoPlanly.`) with
/// `mutable-content`, and this replaces its title and body with the sentence `:push-wording` words
/// from the push's data, in the language the app shares, for the account the app shares — the same
/// wording Android's `PushNotifier` shows (CLAUDE.md item 15).
///
/// It reads the two values the app shares through the App Group (`AppGroupShare.kt`): the signed-in
/// uid and the app's resolved language. A push for another uid, a push while nobody is signed in and
/// a type the wording cannot word are delivered as they came, which is the neutral fallback: iOS
/// shows a push whose extension delivers nothing, so suppressing one needs Apple's filtering
/// entitlement, which the owner has applied for (design §2 decision 4). Running out of time
/// delivers the fallback too.
///
/// Once per push it writes its memory to the system log (`COPLANLY_NSE_MEMORY`), which the
/// `ios-app` CI job reads after its push test (`simulator.sh push-test`): iOS ends an extension past
/// about 24 MB, and the job fails above 20 MB.
final class NotificationService: UNNotificationServiceExtension {
    private static let suite = "group.app.coplanly"
    private static let uidKey = "coplanly.push.uid"
    private static let languageKey = "coplanly.push.language"

    private var contentHandler: ((UNNotificationContent) -> Void)?
    private var fallback: UNNotificationContent?

    override func didReceive(
        _ request: UNNotificationRequest,
        withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void
    ) {
        self.contentHandler = contentHandler
        fallback = request.content
        let shared = UserDefaults(suiteName: Self.suite)
        let text = ExtensionEntryKt.wordPush(
            data: Self.stringData(request.content.userInfo),
            sharedUid: shared?.string(forKey: Self.uidKey),
            language: shared?.string(forKey: Self.languageKey)
        )
        Self.logMemory(worded: text != nil)
        guard let text, let content = request.content.mutableCopy() as? UNMutableNotificationContent else {
            deliver(request.content)
            return
        }
        content.title = text.title
        content.body = text.body
        deliver(content)
    }

    /// Out of time: the push as it came, the server's neutral alert.
    override func serviceExtensionTimeWillExpire() {
        if let fallback { deliver(fallback) }
    }

    /// Hands [content] to iOS once; a second call (the time running out after delivery) does nothing.
    private func deliver(_ content: UNNotificationContent) {
        guard let handler = contentHandler else { return }
        contentHandler = nil
        handler(content)
    }

    /// The push's data as the wording reads it: every string key with a string value (FCM puts the
    /// data keys at the top level of the APNs payload; the `aps` block is Apple's and not a string).
    private static func stringData(_ userInfo: [AnyHashable: Any]) -> [String: String] {
        var data: [String: String] = [:]
        for (key, value) in userInfo {
            if let key = key as? String, let value = value as? String {
                data[key] = value
            }
        }
        return data
    }

    /// One line in the system log with this process's memory, in bytes: its physical footprint (what
    /// iOS holds an extension to), the footprint's peak so far, and the resident size.
    private static func logMemory(worded: Bool) {
        var info = task_vm_info_data_t()
        var count = mach_msg_type_number_t(MemoryLayout<task_vm_info_data_t>.size / MemoryLayout<natural_t>.size)
        let result = withUnsafeMutablePointer(to: &info) { pointer in
            pointer.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
                task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), $0, &count)
            }
        }
        guard result == KERN_SUCCESS else {
            NSLog("COPLANLY_NSE_MEMORY unavailable kern=%d", result)
            return
        }
        let line = "COPLANLY_NSE_MEMORY footprint=\(info.phys_footprint) peak=\(info.ledger_phys_footprint_peak) "
            + "resident=\(info.resident_size) worded=\(worded)"
        NSLog("%@", line)
    }
}
