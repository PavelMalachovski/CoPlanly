import XCTest

/// Pushes on a simulator (iPhone phase 4 part C, Task 6).
///
/// Signed in as the first parent of the family `tools/ios/seed-shell-family.js` seeded (on the
/// Firebase emulators, as `ShellUITests`), each case sends a push the way APNs delivers what the
/// server sends — the neutral alert `CoPlanly` / `You have a new update in CoPlanly.` with
/// `mutable-content`, and the push's data at the top level — and reads the banner SpringBoard shows:
/// - an `expense_added` for this account arrives as a banner;
/// - one for another uid is never worded with this account's data;
/// - a `chat_message`, tapped, opens the app on the family's thread (`IosApp.openPush`).
///
/// **The simulator does not run the Notification Service Extension for `simctl push`**: a locally
/// fed push is shown as it was sent, so a banner here reads the server's neutral alert. The
/// extension's wording is held by `ExtensionEntryTest` and `PushWordingIosTest` (`:push-wording`'s
/// tests on the simulator), and a worded banner on a phone is `docs/DEVICE-CHECKLIST.md` §3.43.
/// Should a later simulator run the extension, the expense case also accepts its worded sentence.
///
/// A UI test cannot send a push itself: `simctl push` runs on the Mac. So the test writes each
/// payload as a JSON file into `COPLANLY_UITEST_PUSH_DIR`, a directory on the Mac the simulator's
/// processes can reach by its path, and `iosApp/scripts/simulator.sh push-test` sends every file that
/// appears there with `xcrun simctl push` while the test runs. The system's notification question is
/// asked at launch through `COPLANLY_UITEST_ASK_NOTIFICATIONS` (AppDelegate) and answered here.
final class PushUITests: XCTestCase {
    private let app = XCUIApplication()
    private let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
    private let fallbackBody = "You have a new update in CoPlanly."

    private var uid = ""
    private var familyId = ""
    private var pushDir = ""

    override func setUpWithError() throws {
        continueAfterFailure = false
        let environment = ProcessInfo.processInfo.environment
        let email = try XCTUnwrap(environment["COPLANLY_UITEST_EMAIL"], "No seeded account was handed to the test")
        let password = try XCTUnwrap(environment["COPLANLY_UITEST_PASSWORD"])
        uid = try XCTUnwrap(environment["COPLANLY_UITEST_UID"], "No seeded uid was handed to the test")
        familyId = try XCTUnwrap(environment["COPLANLY_UITEST_FAMILY_ID"])
        pushDir = try XCTUnwrap(environment["COPLANLY_UITEST_PUSH_DIR"], "No push directory was handed to the test")

        app.launchEnvironment["COPLANLY_EMULATOR_HOST"] = "127.0.0.1"
        app.launchEnvironment["COPLANLY_UITEST_ASK_NOTIFICATIONS"] = "1"
        app.launch()
        allowNotificationsIfAsked()
        try signInIfNeeded(email: email, password: password)
        // The app shares the uid with the extension when the session starts (AppGroupShare); give the
        // write a moment, then leave the app so each banner is SpringBoard's.
        sleep(3)
        XCUIDevice.shared.press(.home)
        _ = springboard.wait(for: .runningForeground, timeout: 10)
    }

    func testAnExpenseForThisAccountArrives() throws {
        try push("expense", data: [
            "type": "expense_added",
            "targetUserId": uid,
            "familyId": familyId,
            "actorName": "Bob",
            "subject": "Groceries",
            "amount": "12.50",
            "currency": "EUR",
            "share": "6.25",
        ])
        // The extension's sentence where the simulator runs it, else the alert as sent.
        let worded = bannerReading("Bob added Groceries")
        let found = waitForEither(worded, bannerReading(fallbackBody), timeout: 60)
        shot("push-expense")
        XCTAssertTrue(found, "No banner showed the expense; SpringBoard showed: \(bannerLabels())")
        if worded.exists {
            XCTAssertTrue(bannerReading("New expense").exists, "The worded banner's title is not \"New expense\"")
        }
    }

    func testAPushForAnotherUidIsNotWorded() throws {
        try push("another-uid", data: [
            "type": "expense_added",
            "targetUserId": "someone-who-signed-out",
            "familyId": familyId,
            "actorName": "Stranger",
            "subject": "Stranger's groceries",
            "amount": "99.00",
            "currency": "EUR",
        ])
        let fallback = bannerReading(fallbackBody)
        let found = fallback.waitForExistence(timeout: 60)
        shot("push-another-uid-fallback")
        XCTAssertTrue(found, "No banner showed the neutral fallback; SpringBoard showed: \(bannerLabels())")
        XCTAssertFalse(bannerReading("Stranger").exists, "A push for another account was worded")
    }

    func testAChatMessageTappedOpensItsThread() throws {
        try push("chat", data: [
            "type": "chat_message",
            "targetUserId": uid,
            "familyId": familyId,
            "conversationId": familyId,
            "actorName": "Bob",
            "preview": "See you at five",
        ])
        // The message where the simulator runs the extension, else the alert as sent.
        let worded = bannerReading("See you at five")
        let found = waitForEither(worded, bannerReading(fallbackBody), timeout: 60)
        shot("push-chat-banner")
        XCTAssertTrue(found, "No banner showed the chat message; SpringBoard showed: \(bannerLabels())")
        (worded.exists ? worded : bannerReading(fallbackBody)).tap()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 30), "Tapping the banner did not open the app")
        let chat = app.tabBars.firstMatch.buttons["Chat"]
        XCTAssertTrue(chat.waitForExistence(timeout: 60), "No tab bar after the tap")
        let selected = NSPredicate(format: "isSelected == true")
        expectation(for: selected, evaluatedWith: chat)
        waitForExpectations(timeout: 30)
        let bar = app.navigationBars.firstMatch
        let name = bar.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", "Bob")).firstMatch
        let named = name.waitForExistence(timeout: 60) || app.navigationBars["Bob"].exists
        shot("push-chat-thread")
        XCTAssertTrue(named, "The tapped push did not open the thread with Bob")
    }

    // MARK: - Helpers

    /// Writes the push [data] carries, as APNs delivers the server's message, for the script to send:
    /// written beside its final name and renamed, so the script never reads half a file.
    private func push(_ name: String, data: [String: String]) throws {
        var payload: [String: Any] = data
        payload["aps"] = [
            "alert": ["title": "CoPlanly", "body": fallbackBody],
            "mutable-content": 1,
            "sound": "default",
        ]
        let json = try JSONSerialization.data(withJSONObject: payload, options: [.sortedKeys])
        let directory = URL(fileURLWithPath: pushDir, isDirectory: true)
        let stamp = Int(Date().timeIntervalSince1970 * 1000)
        let part = directory.appendingPathComponent("\(stamp)-\(name).part")
        try json.write(to: part)
        try FileManager.default.moveItem(at: part, to: directory.appendingPathComponent("\(stamp)-\(name).json"))
        print("COPLANLY_PUSH_REQUESTED \(name)")
    }

    /// Waits until [first] or [second] exists; true when one did.
    private func waitForEither(_ first: XCUIElement, _ second: XCUIElement, timeout: TimeInterval) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if first.exists || second.exists { return true }
            usleep(500_000)
        }
        return first.exists || second.exists
    }

    /// The system's notification question, when it is on screen: allowed.
    private func allowNotificationsIfAsked() {
        let allow = springboard.alerts.buttons["Allow"]
        if allow.waitForExistence(timeout: 15) { allow.tap() }
    }

    private func signInIfNeeded(email: String, password: String) throws {
        let home = app.tabBars.firstMatch.buttons["Home"]
        let welcome = app.staticTexts["Welcome back!"]
        let deadline = Date().addingTimeInterval(120)
        while Date() < deadline, !home.exists, !welcome.exists { usleep(500_000) }
        if home.exists { return }
        XCTAssertTrue(welcome.exists, "Neither the tabs nor the sign-in screen appeared")
        try type(email, into: field(labelled: "Email address"))
        try type(password, into: field(labelled: "Password"))
        if app.keyboards.firstMatch.exists { app.typeText("\n") }
        let signIn = app.buttons.matching(NSPredicate(format: "label == %@", "Sign In")).firstMatch
        XCTAssertTrue(signIn.waitForExistence(timeout: 10), "No Sign In button")
        scrollWhollyIntoView(signIn)
        signIn.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        XCTAssertTrue(home.waitForExistence(timeout: 120), "No tab bar after signing in")
    }

    /// Scrolls the screen until [element] is drawn wholly above the bottom edge's home indicator. The
    /// sign-in form ends with Sign In at the screen's bottom edge since Sign in with Apple joined it,
    /// and a tap on the part the home indicator covers never reaches the button. A swipe leaves the
    /// form flinging after XCUITest calls the app idle, and a tap during a fling only stops it, so this
    /// waits until [element] stays put.
    private func scrollWhollyIntoView(_ element: XCUIElement) {
        let limit = app.windows.firstMatch.frame.maxY - 60
        var swiped = false
        for _ in 0..<4 where element.frame.maxY > limit {
            app.swipeUp()
            swiped = true
        }
        guard swiped else { return }
        var last = element.frame
        for _ in 0..<20 {
            usleep(250_000)
            let now = element.frame
            if now == last { return }
            last = now
        }
    }

    /// A text field by its label, as `ShellUITests` finds Compose's fields.
    private func field(labelled label: String) throws -> XCUIElement {
        let byLabel = NSPredicate(format: "label == %@", label)
        let inside = app.textViews.containing(byLabel).firstMatch
        if inside.waitForExistence(timeout: 30) { return inside }
        let field = app.textFields.matching(byLabel).firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 5), "No field labelled \"\(label)\"")
        return field
    }

    private func type(_ text: String, into field: XCUIElement) throws {
        field.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        _ = app.keyboards.firstMatch.waitForExistence(timeout: 5)
        app.typeText(text)
    }

    /// SpringBoard's element whose label holds [text]: a banner reads its title and body together.
    private func bannerReading(_ text: String) -> XCUIElement {
        springboard.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", text)).firstMatch
    }

    /// What SpringBoard's notification elements read, for a failure's message.
    private func bannerLabels() -> String {
        let notifications = springboard.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier CONTAINS[c] 'Notification' OR label CONTAINS 'CoPlanly'"))
            .allElementsBoundByIndex
        return notifications.map { $0.label }.filter { !$0.isEmpty }.joined(separator: " | ")
    }

    private func shot(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
