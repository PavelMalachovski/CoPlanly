import XCTest

/// The native shell on a simulator, against the Firebase emulators (MON-24 phase 3, Task 8).
///
/// It signs in by e-mail as the first parent of the family `tools/ios/seed-shell-family.js` seeded,
/// and walks the path part A converts — Home, Family, Expenses, the Add expense sheet, Settings —
/// saving a screenshot of each as an attachment, which CI exports as the `ios-shell` artifact. On the
/// way it does what the shell's review is about: a back swipe, a back swipe let go of (over native
/// bars and over a screen that draws its own bar, each checked to have begun and been cancelled), a
/// second tap on the selected tab popping two screens, a sheet holding an edit swiped down, which
/// must ask "Discard changes?" and stay open on "Keep editing", a form that draws its own bar shown
/// with exactly one bar, and a sign-out reaching the sign-in screen with no tabs, then a sign-in
/// landing on fresh tabs.
///
/// **One run reports every problem.** The walk is a list of steps. A step that fails records its
/// reason, a screenshot and the app's accessibility hierarchy, and the walk goes on; each step starts
/// from a known place (the Family tab at its first screen) where it can. A step whose precondition
/// did not pass is reported as not reached, never as passed. The test fails when any step did not
/// pass, and prints one summary line per step.
///
/// The texts it looks for are the shared strings in English, the simulator's language.
final class ShellUITests: XCTestCase {
    private let timeout: TimeInterval = 30
    private let app = XCUIApplication()

    /// Each step's outcome, in order: "passed", "failed: …" or "not reached: …".
    private var outcomes: [(name: String, outcome: String)] = []

    /// The first issue XCUITest recorded itself (a tap it could not make) during the running step.
    private var stepIssue: String?

    override func setUpWithError() throws {
        continueAfterFailure = true
    }

    /// XCUITest's own issues count against the step running.
    override func record(_ issue: XCTIssue) {
        if stepIssue == nil { stepIssue = issue.compactDescription }
        super.record(issue)
    }

    func testShellWalk() throws {
        let environment = ProcessInfo.processInfo.environment
        let email = try XCTUnwrap(environment["COPLANLY_UITEST_EMAIL"], "No seeded account was handed to the test")
        let password = try XCTUnwrap(environment["COPLANLY_UITEST_PASSWORD"])

        // The emulators, never a real project: the build has no GoogleService-Info.plist either.
        app.launchEnvironment["COPLANLY_EMULATOR_HOST"] = "127.0.0.1"
        app.launch()

        step("sign in") { try self.signIn(email: email, password: password) }
        step("Home", after: ["sign in"]) { try self.home() }
        step("Calendar and Chat tabs", after: ["sign in"]) { try self.otherTabs() }
        step("Family", after: ["sign in"]) { try self.family() }
        step("Expenses", after: ["Family"]) { try self.expenses() }
        step("back swipe", after: ["Expenses"]) { try self.backSwipe() }
        step("cancelled back swipe", after: ["Expenses"]) { try self.cancelledBackSwipe() }
        step("Add expense sheet", after: ["Expenses"]) { try self.addExpenseSheet() }
        step("swipe down over an edit asks", after: ["Add expense sheet"]) { try self.dirtySwipeDown() }
        step("Keep editing stays, Discard leaves", after: ["swipe down over an edit asks"]) { try self.keepEditing() }
        step("Settings", after: ["Family"]) { try self.settings() }
        step("cancelled back swipe over a Compose bar", after: ["Settings"]) { try self.cancelledSwipeOverComposeBar() }
        step("a tab tap pops two screens", after: ["Settings"]) { try self.tabTapPopsTwo() }
        step("a form with its own bar shows one bar", after: ["Calendar and Chat tabs"]) { try self.composeBarSheet() }
        step("sign out, and sign in to fresh tabs", after: ["Settings"]) {
            try self.signOutAndIn(email: email, password: password)
        }

        print("COPLANLY_SHELL_WALK_SUMMARY_BEGIN")
        outcomes.forEach { print("COPLANLY_STEP \($0.name): \($0.outcome)") }
        print("COPLANLY_SHELL_WALK_SUMMARY_END")
        let notPassed = outcomes.filter { $0.outcome != "passed" }
        if !notPassed.isEmpty {
            XCTFail("Steps that did not pass: " + notPassed.map { "\($0.name) (\($0.outcome))" }.joined(separator: "; "))
        }
    }

    // MARK: - The steps

    private func signIn(email: String, password: String) throws {
        // The sign-in screen's own text, before its fields: Compose draws the screen first.
        _ = app.staticTexts["Welcome back!"].waitForExistence(timeout: 120)
        shot("00-sign-in")
        try type(email, into: field(labelled: "Email address"))
        try type(password, into: field(labelled: "Password"))
        dismissKeyboard()
        press(try element(labelled: "Sign In", in: app.buttons))
        try require(app.tabBars.firstMatch.buttons["Home"].waitForExistence(timeout: 120), "No tab bar after signing in")
    }

    private func home() throws {
        app.tabBars.firstMatch.buttons["Home"].tap()
        try require(app.navigationBars["Home"].waitForExistence(timeout: timeout), "Home's navigation bar is missing")
        shot("01-home")
    }

    private func otherTabs() throws {
        let tabs = app.tabBars.firstMatch
        tabs.buttons["Calendar"].tap()
        try require(tabs.buttons["Calendar"].isSelected, "The Calendar tab did not select")
        tabs.buttons["Chat"].tap()
        try require(tabs.buttons["Chat"].isSelected, "The Chat tab did not select")
    }

    private func family() throws {
        try goToFamilyRoot()
        shot("02-family")
    }

    private func expenses() throws {
        try goToExpenses()
        shot("03-expenses")
    }

    private func backSwipe() throws {
        try goToExpenses()
        swipeBack(to: 0.85)
        try require(app.navigationBars["Family"].waitForExistence(timeout: timeout), "The back swipe did not pop Expenses")
        // The shared stack followed: the money row opens Expenses again.
        try openExpenses()
    }

    private func cancelledBackSwipe() throws {
        try goToExpenses()
        let before = cancelledTransitions()
        swipeBackAndLetGo(at: 0.2)
        sleep(1)
        try require(app.navigationBars["Expenses"].exists, "A cancelled back swipe popped the screen")
        try requireCancelledTransition(after: before)
        // And the stack still works from there: the back button pops to Family.
        app.navigationBars["Expenses"].buttons.element(boundBy: 0).tap()
        try require(app.navigationBars["Family"].waitForExistence(timeout: timeout), "The back button after a cancelled swipe did not pop")
    }

    private func addExpenseSheet() throws {
        try goToExpenses()
        press(try element(labelled: "Add expense", in: app.buttons))
        let sheetBar = app.navigationBars["Add expense"]
        try require(sheetBar.waitForExistence(timeout: timeout), "The form did not open as a sheet")
        // The form's Save is the sheet bar's, not a button pinned under the form, and an empty
        // form cannot be saved.
        let save = sheetBar.buttons["Save expense"]
        try require(save.waitForExistence(timeout: timeout), "The sheet's bar carries no Save")
        try require(!save.isEnabled, "Save was enabled on an empty form")
        let saves = app.buttons.matching(NSPredicate(format: "label == %@", "Save expense"))
        try require(saves.count == 1, "A second Save was drawn under the form")
        shot("04-add-expense-sheet")
    }

    private func dirtySwipeDown() throws {
        let sheetBar = app.navigationBars["Add expense"]
        if !sheetBar.exists { try addExpenseSheet() }
        try type("Shell check", into: field(labelled: "Title"))
        dismissKeyboard()
        swipeSheetDown(from: sheetBar)
        try require(app.staticTexts["Discard changes?"].waitForExistence(timeout: timeout), "A swipe over edits did not ask first")
        shot("05-discard-question")
    }

    private func keepEditing() throws {
        let sheetBar = app.navigationBars["Add expense"]
        let question = app.staticTexts["Discard changes?"]
        press(try element(labelled: "Keep editing", in: app.buttons))
        sleep(1)
        try require(!question.exists, "Keep editing left the question up")
        try require(sheetBar.exists, "Keep editing closed the sheet")
        // The close button asks too; Discard leaves the form and the sheet.
        closeButton(of: sheetBar).tap()
        try require(question.waitForExistence(timeout: timeout), "Closing over edits did not ask")
        press(try element(labelled: "Discard", in: app.buttons))
        try require(app.navigationBars["Expenses"].waitForExistence(timeout: timeout), "Discard did not close the sheet")
    }

    private func settings() throws {
        try goToFamilyRoot()
        app.navigationBars["Family"].buttons["Settings"].tap()
        try require(app.navigationBars["Settings"].waitForExistence(timeout: timeout), "Settings did not open")
        shot("06-settings")
    }

    private func cancelledSwipeOverComposeBar() throws {
        if !app.navigationBars["Settings"].exists { try settings() }
        try openDataSources()
        let before = cancelledTransitions()
        swipeBackAndLetGo(at: 0.2)
        sleep(1)
        shot("09-compose-bar-after-cancelled-swipe")
        // Data sources stays, with its own bar alone: the system bar is not left in Settings' state.
        try require(!app.navigationBars["Settings"].exists, "A cancelled back swipe popped Data sources")
        try require(app.navigationBars.count == 0, "Two bars after a cancelled swipe: the system bar over a Compose bar")
        let counted = Result { try requireCancelledTransition(after: before) }
        // A swipe that goes back brings Settings with its native bar.
        swipeBack(to: 0.85)
        try require(app.navigationBars["Settings"].waitForExistence(timeout: timeout), "The back swipe from Data sources did not show Settings' bar")
        try counted.get()
    }

    private func tabTapPopsTwo() throws {
        if !app.navigationBars["Settings"].exists { try settings() }
        try openDataSources()
        shot("07-compose-bar-screen")
        app.tabBars.firstMatch.buttons["Family"].tap()
        try require(app.navigationBars["Family"].waitForExistence(timeout: timeout), "The tab tap did not pop to Family")
        // The shared stack followed: Expenses opens and its back returns to Family.
        try openExpenses()
        app.navigationBars["Expenses"].buttons.element(boundBy: 0).tap()
        try require(app.navigationBars["Family"].waitForExistence(timeout: timeout), "Back from Expenses did not return to Family")
        shot("08-family-again")
    }

    private func composeBarSheet() throws {
        // The calendar's month bar carries "Add event" since the tester redesign (November 2026),
        // which took the empty week's action off Home: the Calendar tab, whose header is Compose's.
        try goToCalendarRoot()
        press(try element(labelled: "Add event", in: app.buttons))
        try require(app.staticTexts["New event"].waitForExistence(timeout: timeout), "The event form did not open")
        sleep(1)
        // The form draws its own bar; the sheet's navigation bar stays hidden over it, as the
        // calendar's is behind it: no system bar at all.
        try require(app.navigationBars.count == 0, "A system bar in the event form's sheet")
        shot("10-event-form-sheet")
        press(try element(labelled: "Cancel", in: app.buttons))
        try require(app.staticTexts["New event"].waitForNonExistence(timeout: timeout), "Cancel did not close the event form")
        try require(app.navigationBars.count == 0, "A system bar over the calendar after the event form")
    }

    private func signOutAndIn(email: String, password: String) throws {
        if !app.navigationBars["Settings"].exists { try settings() }
        let row = try element(labelled: "Sign out of app", in: app.buttons)
        let screenBottom = app.windows.firstMatch.frame.maxY - 120
        for _ in 0..<12 where row.frame.maxY > screenBottom {
            app.swipeUp()
        }
        sleep(1)
        press(row)
        // The confirmation's button reads as the row does; it is the last one drawn.
        let named = app.buttons.matching(NSPredicate(format: "label == %@", "Sign out of app"))
        try require(named.element(boundBy: 0).waitForExistence(timeout: timeout), "No sign-out confirmation")
        sleep(1)
        press(named.element(boundBy: named.count - 1))
        try require(app.staticTexts["Welcome back!"].waitForExistence(timeout: 120), "Signing out did not reach the sign-in screen")
        try require(!app.tabBars.firstMatch.exists, "The tab bar is still there on the sign-in screen")
        shot("11-signed-out")
        try signIn(email: email, password: password)
        // Fresh tabs: the Family tab opens at its first screen, not at the Settings it was left on.
        app.tabBars.firstMatch.buttons["Family"].tap()
        try require(app.navigationBars["Family"].waitForExistence(timeout: timeout), "The Family tab did not open at its first screen")
        try require(!app.navigationBars["Settings"].exists, "The Family tab kept the signed-out session's Settings")
        shot("12-fresh-tabs")
    }

    // MARK: - Getting to a known place

    private func goToHomeRoot() throws {
        let home = app.tabBars.firstMatch.buttons["Home"]
        home.tap()
        if !app.navigationBars["Home"].waitForExistence(timeout: 3) { home.tap() }
        try require(app.navigationBars["Home"].waitForExistence(timeout: timeout), "Home's first screen is missing")
    }

    /// The Calendar tab, which draws its own Compose header: no system bar over it.
    private func goToCalendarRoot() throws {
        let calendar = app.tabBars.firstMatch.buttons["Calendar"]
        calendar.tap()
        try require(calendar.waitForExistence(timeout: timeout) && calendar.isSelected, "The Calendar tab did not select")
    }

    /// Opens Data sources from Settings: a screen that draws its own Compose bar, pushed with the
    /// system bar hidden.
    private func openDataSources() throws {
        let dataSources = try element(containing: "Data sources and licences")
        let screenBottom = app.windows.firstMatch.frame.maxY - 120
        for _ in 0..<6 where dataSources.frame.maxY > screenBottom {
            app.swipeUp()
        }
        // A tap while the list still glides after a swipe only stops it: let it settle, and tap
        // once more if the first tap was spent that way.
        sleep(1)
        press(dataSources)
        if !app.navigationBars["Settings"].waitForNonExistence(timeout: 3) { press(dataSources) }
        sleep(2)
        try require(!app.navigationBars["Settings"].exists, "Data sources did not open over Settings")
        try require(app.navigationBars.count == 0, "Two bars: the system bar over a Compose bar")
    }

    /// The Family tab at its first screen, whatever an earlier step left: a question, a sheet, a
    /// pushed screen.
    private func goToFamilyRoot() throws {
        let question = app.staticTexts["Discard changes?"]
        let sheetBar = app.navigationBars["Add expense"]
        if question.exists { pressIfThere("Discard") }
        if sheetBar.exists {
            closeButton(of: sheetBar).tap()
            if question.waitForExistence(timeout: 3) { pressIfThere("Discard") }
            _ = sheetBar.waitForNonExistence(timeout: timeout)
        }
        let family = app.tabBars.firstMatch.buttons["Family"]
        family.tap()
        if !app.navigationBars["Family"].waitForExistence(timeout: 3) {
            // A second tap on the selected tab pops it to its first screen.
            family.tap()
        }
        try require(app.navigationBars["Family"].waitForExistence(timeout: timeout), "The Family tab's first screen is missing")
    }

    private func goToExpenses() throws {
        if app.navigationBars["Expenses"].exists && !app.navigationBars["Add expense"].exists { return }
        try goToFamilyRoot()
        try openExpenses()
    }

    private func openExpenses() throws {
        press(try element(containing: "This month"))
        try require(app.navigationBars["Expenses"].waitForExistence(timeout: timeout), "Expenses did not open")
    }

    /// The cross at the start of a sheet's bar (its Save sits at the other end).
    private func closeButton(of bar: XCUIElement) -> XCUIElement {
        let close = bar.buttons["Close"]
        return close.exists ? close : bar.buttons.firstMatch
    }

    /// Presses the button labelled [label] when one is on screen, and nothing otherwise.
    private func pressIfThere(_ label: String) {
        let button = app.buttons.matching(NSPredicate(format: "label == %@", label)).firstMatch
        if button.waitForExistence(timeout: 3) { press(button) }
    }

    // MARK: - Cancelled transitions

    /// The shell counts each cancelled transition in its tab bar's accessibility identifier.
    private let cancelledPrefix = "coplanly.shell.cancelledTransitions."

    private func cancelledTransitions() -> Int {
        let identifier = app.tabBars.firstMatch.identifier
        guard identifier.hasPrefix(cancelledPrefix) else { return 0 }
        return Int(identifier.dropFirst(cancelledPrefix.count)) ?? 0
    }

    /// Fails unless the shell counted a cancelled transition beyond [count] within a few seconds:
    /// so a let-go swipe that never started a transition cannot pass.
    private func requireCancelledTransition(after count: Int) throws {
        for _ in 0..<10 {
            if cancelledTransitions() > count { return }
            usleep(500_000)
        }
        let identifier = app.tabBars.firstMatch.identifier
        throw StepFailure(description: "The let-go swipe did not start a transition that was cancelled (tab bar identifier \"\(identifier)\")")
    }

    // MARK: - Steps and failures

    private struct StepFailure: Error, CustomStringConvertible {
        let description: String
    }

    private func require(_ condition: Bool, _ message: String) throws {
        if !condition { throw StepFailure(description: message) }
    }

    /// Runs [body] as the step [name], unless a step of [after] did not pass: then it is not reached.
    private func step(_ name: String, after: [String] = [], _ body: () throws -> Void) {
        let missing = after.first { required in
            outcomes.first { $0.name == required }?.outcome != "passed"
        }
        if let missing {
            outcomes.append((name, "not reached: \"\(missing)\" did not pass"))
            return
        }
        stepIssue = nil
        var outcome = "passed"
        XCTContext.runActivity(named: name) { _ in
            do {
                try body()
            } catch {
                outcome = "failed: \(error)"
            }
        }
        if outcome == "passed", let issue = stepIssue { outcome = "failed: \(issue)" }
        if outcome != "passed" {
            shot("zz-failed-" + name.replacingOccurrences(of: " ", with: "-"))
            print("COPLANLY_UI_HIERARCHY_BEGIN \(name)\n\(app.debugDescription)\nCOPLANLY_UI_HIERARCHY_END")
        }
        outcomes.append((name, outcome))
    }

    // MARK: - Finding and touching

    /// A text field by its label. Compose exposes a text field as a text view whose label is the
    /// static text inside it; a text field proper is looked for too.
    private func field(labelled label: String) throws -> XCUIElement {
        let byLabel = NSPredicate(format: "label == %@", label)
        let inside = app.textViews.containing(byLabel).firstMatch
        if inside.waitForExistence(timeout: timeout) { return inside }
        let field = app.textFields.matching(byLabel).firstMatch
        try require(field.waitForExistence(timeout: 5), "No field labelled \"\(label)\"")
        return field
    }

    /// Types [text] into [field] through the keyboard, once a tap has focused it.
    private func type(_ text: String, into field: XCUIElement) throws {
        press(field)
        _ = app.keyboards.firstMatch.waitForExistence(timeout: 5)
        app.typeText(text)
    }

    /// Closes the keyboard with its own key, so what it covered can be tapped.
    private func dismissKeyboard() {
        let keyboard = app.keyboards.firstMatch
        guard keyboard.exists else { return }
        app.typeText("\n")
        _ = keyboard.waitForNonExistence(timeout: 5)
    }

    /// Taps [element] where it is drawn. Compose's elements are not always reported hittable, so a
    /// tap at their centre stands in for XCUITest's own hit test.
    private func press(_ element: XCUIElement) {
        if element.isHittable {
            element.tap()
        } else {
            element.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        }
    }

    /// The first element of [query] labelled [label] exactly, or else any element whose label holds it.
    private func element(labelled label: String, in query: XCUIElementQuery) throws -> XCUIElement {
        let exact = query.matching(NSPredicate(format: "label == %@", label)).firstMatch
        if exact.waitForExistence(timeout: 10) { return exact }
        return try element(containing: label)
    }

    private func element(containing text: String) throws -> XCUIElement {
        let match = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", text))
            .firstMatch
        try require(match.waitForExistence(timeout: timeout), "Nothing on screen reads \"\(text)\"")
        return match
    }

    /// A quick drag from the left edge to [fraction] of the width, which pops.
    private func swipeBack(to fraction: CGFloat) {
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.0, dy: 0.5))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: fraction, dy: 0.5))
        start.press(forDuration: 0.05, thenDragTo: end)
    }

    /// A slow drag from the left edge to [fraction] of the width, held still and let go of: UIKit
    /// cancels the pop it started, as it does under a finger that changes its mind.
    private func swipeBackAndLetGo(at fraction: CGFloat) {
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.0, dy: 0.5))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: fraction, dy: 0.5))
        start.press(forDuration: 0.1, thenDragTo: end, withVelocity: XCUIGestureVelocity(60), thenHoldForDuration: 0.8)
    }

    private func swipeSheetDown(from bar: XCUIElement) {
        let start = bar.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.2))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.95))
        start.press(forDuration: 0.05, thenDragTo: end)
    }

    private func shot(_ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
