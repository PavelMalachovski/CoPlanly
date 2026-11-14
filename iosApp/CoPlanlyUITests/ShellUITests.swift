import XCTest

/// The native shell on a simulator, against the Firebase emulators (MON-24 phase 3, Task 8).
///
/// It signs in by e-mail as the first parent of the family `tools/ios/seed-shell-family.js` seeded,
/// and walks the path part A converts — Home, Family, Expenses, the Add expense sheet, Settings —
/// saving a screenshot of each as an attachment, which CI exports as the `ios-shell` artifact. On the
/// way it does what the shell's review is about: a back swipe, a back swipe let go of (over native
/// bars and over a screen that draws its own bar, each checked to have begun and been cancelled), a
/// second tap on the selected tab popping two screens, the Chat tab's thread under the system bar with
/// its overflow menu opening and closing, a sheet holding an edit swiped down, which
/// must ask "Discard changes?" and stay open on "Keep editing", the event form as a sheet with Save in
/// its bar (disabled until the event has a title), New event's description and the chat composer
/// staying above the keyboard, a `coplanly://pair` link reaching the pairing screen with its code, and
/// a sign-out reaching the sign-in screen with no tabs, then a
/// sign-in landing on fresh tabs.
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
        step("Chat thread's bar and overflow menu", after: ["Calendar and Chat tabs"]) { try self.chatThreadBar() }
        step("Calendar's bar shows the month and its actions", after: ["Calendar and Chat tabs"]) { try self.calendarBar() }
        step("Family", after: ["sign in"]) { try self.family() }
        step("Expenses", after: ["Family"]) { try self.expenses() }
        step("back swipe", after: ["Expenses"]) { try self.backSwipe() }
        step("cancelled back swipe", after: ["Expenses"]) { try self.cancelledBackSwipe() }
        step("Add expense sheet", after: ["Expenses"]) { try self.addExpenseSheet() }
        step("swipe down over an edit asks", after: ["Add expense sheet"]) { try self.dirtySwipeDown() }
        step("Keep editing stays, Discard leaves", after: ["swipe down over an edit asks"]) { try self.keepEditing() }
        step("back undoes a screen's inner step", after: ["Family"]) { try self.innerBackStep() }
        step("Settings", after: ["Family"]) { try self.settings() }
        step("cancelled back swipe over a sub-screen", after: ["Settings"]) { try self.cancelledSwipeOverSubScreen() }
        step("a tab tap pops two screens", after: ["Settings"]) { try self.tabTapPopsTwo() }
        step("New event is a sheet with Save in its bar", after: ["Calendar and Chat tabs"]) { try self.eventFormSheet() }
        step("a new event swiped down closes without asking", after: ["New event is a sheet with Save in its bar"]) {
            try self.newEventSwipeDown()
        }
        step("New event's description stays above the keyboard", after: ["New event is a sheet with Save in its bar"]) {
            try self.eventDescriptionAboveKeyboard()
        }
        step("a coplanly://pair link opens Pairing with its code", after: ["sign in"]) { try self.pairingLink() }
        step("sign out, and sign in to fresh tabs", after: ["Settings"]) {
            try self.signOutAndIn(email: email, password: password)
        }
        // Last: the composer's field takes a return as a new line, so the keyboard it opens stays up
        // over the tab bar, and no step could tap a tab after it.
        step("the chat composer stays above the keyboard", after: ["Calendar and Chat tabs"]) {
            try self.chatComposerAboveKeyboard()
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

    /// The Chat tab renders the family's one thread in place under the system bar (MON-24 phase 3,
    /// part B, Task 7): the co-parent's name in the bar's title view, and the overflow menu, which
    /// opens and closes.
    private func chatThreadBar() throws {
        let chat = app.tabBars.firstMatch.buttons["Chat"]
        chat.tap()
        try require(chat.isSelected, "The Chat tab did not select")
        let bar = app.navigationBars.firstMatch
        let name = bar.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", "Bob")).firstMatch
        let named = name.waitForExistence(timeout: 60) || app.navigationBars["Bob"].exists
        shot("15-chat-thread")
        try require(named, "The thread's bar does not show the co-parent's name")
        let more = bar.buttons["More actions"]
        try require(more.waitForExistence(timeout: timeout), "The thread's bar has no overflow button")
        more.tap()
        let block = app.buttons.matching(NSPredicate(format: "label == %@", "Block and stop sharing")).firstMatch
        try require(block.waitForExistence(timeout: timeout), "The overflow menu did not open")
        shot("16-chat-overflow")
        // A tap outside a menu dismisses it, and does nothing else.
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.3)).tap()
        try require(block.waitForNonExistence(timeout: timeout), "The overflow menu did not close")
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

    /// Custody setup with one of its parts open: the edge swipe is refused rather than leaving the
    /// screen with the part half-closed, Back returns to the overview, and from the overview the
    /// swipe leaves. While a part is open its back is the shell's own button labelled Back.
    private func innerBackStep() throws {
        try openCustodySetup()
        press(try element(containing: "Contact windows"))
        try require(waitForNoLabel(containing: "Main schedule"), "Contact windows did not open over the overview")
        shot("13-custody-part-open")
        swipeBack(to: 0.85)
        sleep(2)
        try require(!app.navigationBars["Family"].exists, "The edge swipe left custody setup with a part open")
        try require(!labelExists(containing: "Main schedule"), "The edge swipe closed the part it should have left alone")
        press(try element(labelled: "Back", in: app.buttons))
        try require(element(labelledContaining: "Main schedule").waitForExistence(timeout: timeout), "Back did not return to the overview")
        try require(!app.navigationBars["Family"].exists, "Back left custody setup instead of closing the part")
        shot("14-custody-overview-after-back")
        swipeBack(to: 0.85)
        try require(app.navigationBars["Family"].waitForExistence(timeout: timeout), "The edge swipe from the overview did not leave custody setup")
    }

    private func settings() throws {
        try goToFamilyRoot()
        app.navigationBars["Family"].buttons["Settings"].tap()
        try require(app.navigationBars["Settings"].waitForExistence(timeout: timeout), "Settings did not open")
        shot("06-settings")
    }

    private func cancelledSwipeOverSubScreen() throws {
        if !app.navigationBars["Settings"].exists { try settings() }
        try openDataSources()
        let before = cancelledTransitions()
        swipeBackAndLetGo(at: 0.2)
        sleep(1)
        shot("09-sub-screen-after-cancelled-swipe")
        // Data sources stays, under its own system bar: the bar is not left in Settings' state.
        try require(!app.navigationBars["Settings"].exists, "A cancelled back swipe popped Data sources")
        try require(app.navigationBars[dataSourcesTitle].exists, "A cancelled back swipe left the bar in Settings' state")
        try require(app.navigationBars.count == 1, "Two bars after a cancelled swipe")
        let counted = Result { try requireCancelledTransition(after: before) }
        // A swipe that goes back brings Settings with its native bar.
        swipeBack(to: 0.85)
        try require(app.navigationBars["Settings"].waitForExistence(timeout: timeout), "The back swipe from Data sources did not show Settings' bar")
        try counted.get()
    }

    private func tabTapPopsTwo() throws {
        if !app.navigationBars["Settings"].exists { try settings() }
        try openDataSources()
        shot("07-sub-screen")
        app.tabBars.firstMatch.buttons["Family"].tap()
        try require(app.navigationBars["Family"].waitForExistence(timeout: timeout), "The tab tap did not pop to Family")
        // The shared stack followed: Expenses opens and its back returns to Family.
        try openExpenses()
        app.navigationBars["Expenses"].buttons.element(boundBy: 0).tap()
        try require(app.navigationBars["Family"].waitForExistence(timeout: timeout), "Back from Expenses did not return to Family")
        shot("08-family-again")
    }

    /// The Calendar tab's system bar (MON-24 phase 3, part B, Task 8): titled with the month on
    /// screen, this month at first, with Today, Filters, Settings and "+" as its buttons; the brand
    /// row and its heading are not drawn, and the Month/Week/Day control stays under the bar.
    private func calendarBar() throws {
        try goToCalendarRoot()
        let bar = app.navigationBars[calendarMonthTitle]
        try require(bar.waitForExistence(timeout: 60), "The calendar's bar is not titled \(calendarMonthTitle)")
        shot("17-calendar-bar")
        for label in ["Today", "Filters", "Settings", "Add event"] {
            try require(bar.buttons[label].exists, "The calendar's bar has no \(label) button")
        }
        try require(!labelExists(containing: "Your family calendar"), "The calendar still draws its heading under the bar")
        try require(element(labelledContaining: "Week").exists, "The Month/Week/Day control is missing under the bar")
    }

    /// The month the calendar opens on, as its bar titles it: this month's name, in English.
    private var calendarMonthTitle: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en")
        formatter.dateFormat = "LLLL"
        return formatter.string(from: Date())
    }

    /// The event form, opened from the calendar's bar, is a sheet whose bar carries a cross
    /// and Save (MON-24 phase 3, part B): Save is disabled until the event has a title, and no second
    /// Save is drawn under the form.
    private func eventFormSheet() throws {
        try goToCalendarRoot()
        press(try element(labelled: "Add event", in: app.buttons))
        let sheetBar = app.navigationBars["New event"]
        try require(sheetBar.waitForExistence(timeout: timeout), "The event form did not open as a sheet")
        // The calendar's own bar may still be in the hierarchy under the sheet; nothing else may be.
        let underSheet = app.navigationBars[calendarMonthTitle].exists ? 1 : 0
        try require(app.navigationBars.count - underSheet == 1, "More than one bar over the event form")
        let save = sheetBar.buttons["Save event"]
        try require(save.waitForExistence(timeout: timeout), "The sheet's bar carries no Save")
        try require(!save.isEnabled, "Save was enabled on an event with no title")
        let saves = app.buttons.matching(NSPredicate(format: "label == %@", "Save event"))
        try require(saves.count == 1, "A second Save was drawn under the form")
        try require(sheetBar.buttons["Close"].exists, "The sheet's bar has no close button")
        shot("10-event-form-sheet")
        try type("Shell event", into: field(labelled: "Event title"))
        dismissKeyboard()
        try require(waitUntilEnabled(save), "Save stayed disabled once the event had a title")
        shot("10b-event-form-with-title")
    }

    /// A new event asks nothing when it is left: its draft is kept as the parent types and comes
    /// back on the next "+" (CLAUDE.md design item 16), so a swipe down closes the sheet at once.
    private func newEventSwipeDown() throws {
        let sheetBar = app.navigationBars["New event"]
        if !sheetBar.exists { try eventFormSheet() }
        swipeSheetDown(from: sheetBar)
        try require(sheetBar.waitForNonExistence(timeout: timeout), "A swipe down did not close the new event's sheet")
        try require(!app.staticTexts["Discard changes?"].exists, "A new event asked before closing")
        try require(app.navigationBars[calendarMonthTitle].waitForExistence(timeout: timeout), "The calendar's bar did not come back after the event form")
        try require(app.navigationBars.count == 1, "A second bar over the calendar after the event form")
        try require(app.tabBars.firstMatch.buttons["Calendar"].isSelected, "The calendar was not under the sheet")
    }

    /// The keyboard is taken once, by the shell's screen frame (MON-24 phase 3, part B, Task 9):
    /// typing into New event's description leaves the field above the keys. The labels are the
    /// shared strings in English.
    private func eventDescriptionAboveKeyboard() throws {
        try goToCalendarRoot()
        press(try element(labelled: "Add event", in: app.buttons))
        let sheetBar = app.navigationBars["New event"]
        try require(sheetBar.waitForExistence(timeout: timeout), "The event form did not open as a sheet")
        // A draft that already holds a description shows its field; otherwise a button opens it.
        let open = app.buttons.matching(NSPredicate(format: "label == %@", "Add a description")).firstMatch
        if open.waitForExistence(timeout: 5) { press(open) }
        let description = try field(labelled: "Description (optional)")
        try type("Keyboard check", into: description)
        try require(app.keyboards.firstMatch.waitForExistence(timeout: timeout), "No keyboard over the description")
        shot("18-event-description-keyboard")
        try require(isClearOfKeyboard(description), "The description field is under the keyboard")
        try require(sheetBar.buttons["Save event"].exists, "The sheet's Save left its bar while typing")
        // The description's return closes the keyboard; a new event then closes without asking.
        dismissKeyboard()
        swipeSheetDown(from: sheetBar)
        try require(sheetBar.waitForNonExistence(timeout: timeout), "The event form did not close after the check")
    }

    /// The Chat tab's composer stays above the keyboard and its send button can be tapped there
    /// (MON-24 phase 3, part B, Task 9). The labels are the shared strings in English; nothing is sent,
    /// and what was typed is deleted again so no draft is kept.
    private func chatComposerAboveKeyboard() throws {
        let chat = app.tabBars.firstMatch.buttons["Chat"]
        try require(chat.waitForExistence(timeout: timeout), "No Chat tab")
        chat.tap()
        try require(chat.isSelected, "The Chat tab did not select")
        let composer = try field(labelled: "Type a message…")
        let text = "Keyboard check"
        try type(text, into: composer)
        try require(app.keyboards.firstMatch.waitForExistence(timeout: timeout), "No keyboard over the composer")
        let send = app.buttons.matching(NSPredicate(format: "label == %@", "Send")).firstMatch
        try require(send.waitForExistence(timeout: timeout), "The composer has no send button")
        shot("19-chat-composer-keyboard")
        try require(isClearOfKeyboard(send), "The send button is under the keyboard")
        app.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: text.count))
    }

    /// Whether [element] is drawn on screen and wholly above the keyboard, so a finger can reach it.
    /// Compose's elements are not always reported hittable, so the frames decide.
    private func isClearOfKeyboard(_ element: XCUIElement) -> Bool {
        let keyboard = app.keyboards.firstMatch
        guard element.exists, keyboard.exists else { return false }
        let frame = element.frame
        return !frame.isEmpty && frame.minY >= 0 && frame.maxY <= keyboard.frame.minY
    }

    /// A `coplanly://pair` link opened while signed in reaches the pairing screen with its code
    /// (MON-24 phase 3, part B, Task 10). The code is well formed (`InviteCodeGenerator`'s alphabet
    /// has no 1, 0, I or O) but the emulators hold no invitation for it, so the screen's redeem answers
    /// "No invitation matches that code." — or, where the preview could not say, asks "Link with this
    /// invite code?" — either of which only a code that reached Pairing produces. The texts are the
    /// shared strings in English.
    private func pairingLink() throws {
        try goToFamilyRoot()
        app.open(URL(string: "coplanly://pair?code=ABC234")!)
        let refused = element(labelledContaining: "No invitation matches that code")
        let asked = element(labelledContaining: "Link with")
        var reached = false
        for _ in 0..<60 where !reached {
            reached = refused.exists || asked.exists
            if !reached { sleep(1) }
        }
        shot("16-pairing-link")
        try require(reached, "The link did not reach the pairing screen with its code")
        if asked.exists { pressIfThere("Cancel") }
        try goToFamilyRoot()
    }

    /// Whether [button] becomes enabled within the step timeout.
    private func waitUntilEnabled(_ button: XCUIElement) -> Bool {
        let enabled = XCTNSPredicateExpectation(predicate: NSPredicate(format: "isEnabled == true"), object: button)
        return XCTWaiter().wait(for: [enabled], timeout: timeout) == .completed
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

    /// The Calendar tab's first screen, under its system bar (MON-24 phase 3, part B, Task 8).
    private func goToCalendarRoot() throws {
        let calendar = app.tabBars.firstMatch.buttons["Calendar"]
        calendar.tap()
        try require(calendar.waitForExistence(timeout: timeout) && calendar.isSelected, "The Calendar tab did not select")
    }

    /// Data sources' title, which its system bar shows (MON-24 phase 3, part B, Task 6).
    private let dataSourcesTitle = "Data sources and licences"

    /// Opens Data sources from Settings: a sub-screen under its own system bar, with the back button.
    private func openDataSources() throws {
        let dataSources = try element(containing: dataSourcesTitle)
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
        try require(app.navigationBars[dataSourcesTitle].waitForExistence(timeout: timeout), "Data sources has no system bar")
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

    /// Opens custody setup from the Family tab's agreements, under its own system bar.
    private func openCustodySetup() throws {
        try goToFamilyRoot()
        let row = try element(containing: "Custody schedule")
        let screenBottom = app.windows.firstMatch.frame.maxY - 120
        for _ in 0..<6 where row.frame.maxY > screenBottom {
            app.swipeUp()
        }
        sleep(1)
        press(row)
        if !app.navigationBars["Family"].waitForNonExistence(timeout: 3) { press(row) }
        try require(element(labelledContaining: "Main schedule").waitForExistence(timeout: timeout), "Custody setup did not open at its overview")
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

    /// The first element whose label holds [text], found or not.
    private func element(labelledContaining text: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", text)).firstMatch
    }

    private func labelExists(containing text: String) -> Bool {
        element(labelledContaining: text).exists
    }

    /// Whether nothing on screen reads [text] within the step timeout.
    private func waitForNoLabel(containing text: String) -> Bool {
        element(labelledContaining: text).waitForNonExistence(timeout: timeout)
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
