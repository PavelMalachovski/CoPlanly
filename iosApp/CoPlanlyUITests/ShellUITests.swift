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
/// its bar (disabled until the event has a title), its Starts date picked in the system's own
/// calendar, "Jump to date" as a sheet a swipe down closes, an event's delete asked in the system's
/// action sheet and cancelled ("Discard changes?" being its alert), New event's description and the chat composer
/// staying above the keyboard, a `coplanly://pair` link reaching the pairing screen with its code, the
/// vault's document picker opened and cancelled, an invitation's share sheet opened and closed, and
/// a sign-out reaching the sign-in screen with no tabs, then a
/// sign-in landing on fresh tabs, and last, deleting the account from Settings, which reaches the
/// sign-in screen.
///
/// **One run reports every problem.** The walk is a list of steps. A step that fails records its
/// reason, a screenshot and the app's accessibility hierarchy, and the walk goes on; each step starts
/// from a known place (the Family tab at its first screen) where it can. A step whose precondition
/// did not pass is reported as not reached, never as passed. The test fails when any step did not
/// pass, and prints one summary line per step.
///
/// The texts it looks for are the shared strings in English, the simulator's language.
///
/// `testTour` is the second method (MON-24 phase 3, part B, Task 13): a screenshot of every
/// destination it can reach, in CI's own step and on a family of its own (see its KDoc).
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

    /// XCUITest's own issues count against the step running. While the tour runs they are the
    /// reason its current destination is skipped instead, never a failure (see `testTour`).
    override func record(_ issue: XCTIssue) {
        if touring {
            if tourIssue == nil { tourIssue = issue.compactDescription }
            return
        }
        if stepIssue == nil { stepIssue = issue.compactDescription }
        super.record(issue)
    }

    /// The jump-to-date sheet alone: sign in, open it from the month title, close it twice. A
    /// short reproduction of the walk's "Jump to date" step for a local run (`simulator.sh jump-test`).
    func testJumpToDate() throws {
        let environment = ProcessInfo.processInfo.environment
        let email = try XCTUnwrap(environment["COPLANLY_UITEST_EMAIL"], "No seeded account was handed to the test")
        let password = try XCTUnwrap(environment["COPLANLY_UITEST_PASSWORD"])
        app.launchEnvironment["COPLANLY_EMULATOR_HOST"] = "127.0.0.1"
        app.launch()
        try signIn(email: email, password: password)
        try jumpToDateSwipeDown()
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
        step("Calendar leads with the redesign's header", after: ["Calendar and Chat tabs"]) { try self.calendarHeader() }
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
        step("Settings' Sign-in methods lists the e-mail the walk signed in with", after: ["Settings"]) {
            try self.signInMethods()
        }
        step("New event is a sheet with Save in its bar", after: ["Calendar and Chat tabs"]) { try self.eventFormSheet() }
        step("New event's Starts date is the system's pill", after: ["New event is a sheet with Save in its bar"]) {
            try self.startsDatePill()
        }
        step("New event's Repeat opens the system's menu", after: ["New event is a sheet with Save in its bar"]) {
            try self.repeatMenu()
        }
        step("a new event swiped down closes without asking", after: ["New event is a sheet with Save in its bar"]) {
            try self.newEventSwipeDown()
        }
        step("New event's description stays above the keyboard", after: ["New event is a sheet with Save in its bar"]) {
            try self.eventDescriptionAboveKeyboard()
        }
        // After the event form's steps: they reuse its open sheet, and this one needs the calendar bare.
        step("Jump to date is a sheet a swipe closes", after: ["Calendar and Chat tabs"]) { try self.jumpToDateSwipeDown() }
        step("deleting an event asks in an action sheet, and Cancel keeps it", after: ["Jump to date is a sheet a swipe closes"]) {
            try self.deleteEventAsks()
        }
        step("an event's preview is the system's half-height sheet, swiped away and opened again", after: [
            "deleting an event asks in an action sheet, and Cancel keeps it",
        ]) {
            try self.eventPreviewSheet()
        }
        step("a coplanly://pair link opens Pairing with its code", after: ["sign in"]) { try self.pairingLink() }
        step("Documents' Add a file opens the document picker, and Cancel returns", after: ["Family"]) {
            try self.documentPicker()
        }
        step("an invitation's Share opens the share sheet, and it closes", after: ["Family"]) {
            try self.inviteShareSheet()
        }
        step("sign out, and sign in to fresh tabs", after: ["Settings"]) {
            try self.signOutAndIn(email: email, password: password)
        }
        // Last: the composer's field takes a return as a new line, so the keyboard it opens stays up
        // over the tab bar, and no step could tap a tab after it.
        step("the chat composer stays above the keyboard", after: ["Calendar and Chat tabs"]) {
            try self.chatComposerAboveKeyboard()
        }
        // After everything, on purpose: deleting the account erases the seeded parent the walk signed
        // in as, so no step could sign in again after it. It relaunches the app first, which closes
        // the composer's keyboard and keeps the session. The seeded family is used by nothing after
        // the walk: the emulator tests that follow in CI run on fresh emulators of their own.
        step("delete the account and reach sign-in", after: ["sign in"]) { try self.deleteAccount() }

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
        // The tour shoots this screen as `tour-auth`; its artifact holds `tour-` shots alone.
        if !touring { shot("00-sign-in") }
        try type(email, into: field(labelled: "Email address"))
        try type(password, into: field(labelled: "Password"))
        dismissKeyboard()
        let signInButton = try element(labelled: "Sign In", in: app.buttons)
        scrollWhollyIntoView(signInButton)
        press(signInButton)
        try require(app.tabBars.firstMatch.buttons["Home"].waitForExistence(timeout: 120), "No tab bar after signing in")
    }

    private func home() throws {
        app.tabBars.firstMatch.buttons["Home"].tap()
        try requireHeaderRoot("Home")
        shot("01-home")
    }

    /// The first screen of the Home or the Calendar tab (MON-24 phase 3, part C, Task 8): it leads
    /// with the redesign's wordmark header, whose gear is a "Settings" button in content, and the
    /// shell shows no navigation bar over it.
    private func requireHeaderRoot(_ name: String) throws {
        let gear = app.buttons.matching(NSPredicate(format: "label == %@", "Settings")).firstMatch
        try require(gear.waitForExistence(timeout: timeout), "\(name)'s first screen has no Settings button")
        // The bar of a screen just popped can still be animating away: wait for it rather than look once.
        try require(waitForNoNavigationBar(), "\(name)'s first screen still shows a navigation bar")
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
        // Diagnostics: an unchanged count means the swipe never began a pop; one more means UIKit
        // cancelled it (the app's COPLANLY_SWIPE lines say which condition, or which state).
        let before = cancelledTransitions()
        print("COPLANLY_SWIPE test back swipe cancelledTransitions before=\(before)")
        swipeBack(to: 0.85)
        let popped = app.navigationBars["Family"].waitForExistence(timeout: timeout)
        print("COPLANLY_SWIPE test back swipe cancelledTransitions after=\(cancelledTransitions()) popped=\(popped)")
        try require(popped, "The back swipe did not pop Expenses")
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
        // A question with two answers is the system's alert, not an action sheet (part C, Task 5).
        try require(app.alerts["Discard changes?"].exists, "Discard changes? is not the system's alert")
        try require(!app.sheets.firstMatch.exists, "Discard changes? was asked in an action sheet")
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

    /// The Calendar tab leads with its header (MON-24 phase 3, part C, Task 8; calendar K1), under no
    /// navigation bar: the month with its year, "?" (what the colours mean, with Filters under it)
    /// and Settings, then the month card, whose own bar holds the "Month ▾" pop-up button, Today and "+".
    private func calendarHeader() throws {
        try goToCalendarRoot()
        try require(calendarHeading.waitForExistence(timeout: 60), "The calendar does not lead with \(calendarMonthTitle)")
        shot("17-calendar-header")
        try requireHeaderRoot("The Calendar")
        for label in ["Settings", "What the colours mean", "Today", "Add event"] {
            let button = app.buttons.matching(NSPredicate(format: "label == %@", label)).firstMatch
            try require(button.exists, "The calendar has no \(label) button in content")
        }
        try require(viewButton.exists, "The calendar's view button (\"View: Month\") is missing")
    }

    /// The grid card's view button, the system's pop-up button that reads "View: Month" (24 November
    /// 2026; the "M | W | D" segments before it).
    private var viewButton: XCUIElement {
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "View:")).firstMatch
    }

    /// The view button once it reads [name]: "View: Week".
    private func viewButton(reading name: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label == %@", "View: \(name)")).firstMatch
    }

    /// The calendar's heading, the month title that leads its header (calendar K1).
    private var calendarHeading: XCUIElement { calendarMonthButton }

    /// The header's month title, a button that opens "Jump to date": this month while nothing moved it.
    private var calendarMonthButton: XCUIElement {
        app.buttons.matching(NSPredicate(format: "label == %@", calendarMonthTitle)).firstMatch
    }

    /// The month the calendar opens on, as its header titles it: this month's name and year, in English.
    ///
    /// Two assumptions, both true of CI's simulator: the app speaks English (the simulator's
    /// language, so the shared `calendarMonthTitle` names the month as `en` does, always with its
    /// year since calendar K1), and the walk does not run across midnight at a month's end, when the
    /// app and this test could read two different months. The test's clock is the simulator's, as
    /// the app's is.
    private var calendarMonthTitle: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en")
        formatter.dateFormat = "LLLL yyyy"
        return formatter.string(from: Date())
    }

    /// The event form, opened from the "+" of the calendar's month card, is a sheet whose bar carries a cross
    /// and Save (MON-24 phase 3, part B): Save is disabled until the event has a title, and no second
    /// Save is drawn under the form.
    private func eventFormSheet() throws {
        try goToCalendarRoot()
        press(try element(labelled: "Add event", in: app.buttons))
        let sheetBar = app.navigationBars["New event"]
        try require(sheetBar.waitForExistence(timeout: timeout), "The event form did not open as a sheet")
        // The calendar shows no navigation bar of its own under the sheet: the sheet's is the one bar.
        try require(app.navigationBars.count == 1, "More than one bar over the event form")
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

    /// The event form's Starts date is the system's compact date picker (MON-24 phase 3, part C,
    /// Task 3): tapping it opens the system's own calendar, and the day chosen there is the day the
    /// pill reads. The day is the 20th, or the 21st when today is the 20th, so the pick is a change.
    private func startsDatePill() throws {
        let sheetBar = app.navigationBars["New event"]
        if !sheetBar.exists { try eventFormSheet() }
        dismissKeyboard()
        let byStarts = app.datePickers.matching(NSPredicate(format: "label == %@", "Starts")).firstMatch
        let pill = byStarts.waitForExistence(timeout: 5) ? byStarts : app.datePickers.firstMatch
        try require(pill.waitForExistence(timeout: timeout), "The Starts date is not the system's date picker")
        // The title's keyboard may stay up (its key is Next, not Done): the pill is brought above it.
        try bringIntoForm(pill, what: "the Starts pill")
        let day = Calendar.current.component(.day, from: Date()) == 20 ? 21 : 20
        // The calendar grid behind the sheet has buttons for the day too: what is on screen now is
        // not the system's calendar, which opens on the tap.
        let before = Set(dayButtons(day).map { "\($0.label)@\($0.frame)" })
        press(pill)
        // Opened or not, the calendar is closed again if the step fails, so it does not cascade.
        do {
            let dayButton = try calendarDay(day, excluding: before)
            shot("10c-starts-system-calendar")
            press(dayButton)
            // On some releases the calendar closes on a pick; on iOS 26 it stays up until a tap
            // outside it, and its dismiss region carries no "PopoverDismissRegion" name.
            closeCalendarPopover()
            try require(!calendarPopoverOpen, "The system's calendar stayed open after a tap outside it")
        } catch {
            closeCalendarPopover()
            throw error
        }
        // VoiceOver reads the pill as the value of its "Date Picker" child ("11/20/26", or
        // "Nov 20, 2026"), which is a button while the calendar is closed and another element
        // while it is open: every child is read, whatever its type.
        let children = pill.descendants(matching: .any).allElementsBoundByIndex
        let reads = [pill.label, pill.value as? String ?? ""]
            + children.flatMap { [$0.label, $0.value as? String ?? ""] }.filter { !$0.isEmpty }
        try require(
            reads.contains { $0.range(of: "\\b\(day)\\b", options: .regularExpression) != nil },
            "The Starts pill does not read the \(day)th: \(reads)"
        )
        shot("10d-starts-pill-picked")
    }

    /// Scrolls the open form until [element] is drawn between the sheet's bar and the keyboard (or
    /// the screen's bottom): the form's viewport ends above a keyboard that stays up, and a native
    /// control outside it is not even in the tree.
    private func bringIntoForm(_ element: XCUIElement, what: String) throws {
        let barBottom = app.navigationBars.firstMatch.exists ? app.navigationBars.firstMatch.frame.maxY : 0
        for _ in 0..<10 {
            let keyboard = app.keyboards.firstMatch
            let limit = keyboard.exists ? keyboard.frame.minY - 60 : app.windows.firstMatch.frame.maxY - 160
            if element.exists {
                let frame = element.frame
                if frame.minY >= barBottom && frame.maxY <= limit { return }
                if frame.minY < barBottom {
                    dragForm(from: 0.3, to: 0.42)
                    continue
                }
            }
            dragForm(from: 0.42, to: 0.3)
        }
        try require(element.exists, "\(what) is not in the form")
    }

    /// Drags the form from [from] to [to] (fractions of the screen's height, at its middle): a short
    /// drag, so an element in view is not scrolled past the bar.
    private func dragForm(from: CGFloat, to: CGFloat) {
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: from))
        start.press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: to)))
        usleep(500_000)
    }

    /// The buttons whose label names [day], wholly inside the window.
    private func dayButtons(_ day: Int) -> [XCUIElement] {
        let predicate = NSPredicate(format: "label MATCHES %@", ".*\\b\(day)\\b.*")
        let window = app.windows.firstMatch.frame
        return app.buttons.matching(predicate).allElementsBoundByIndex.filter { element in
            let f = element.frame
            return element.exists && !f.isEmpty && f.minX >= 0 && f.maxX <= window.width
                && f.minY >= 0 && f.maxY <= window.height
        }
    }

    /// The button of the opened calendar for [day], chosen by frame: wholly inside the window, not
    /// one of [excluding] (what was on screen before the calendar opened: the grid behind the
    /// sheet), and preferably inside the system's date picker and of the month the pill shows.
    /// Neighbouring months are kept as off-screen pages whose days match the label too, and
    /// Compose's elements are not reliably reported hittable.
    private func calendarDay(_ day: Int, excluding: Set<String>) throws -> XCUIElement {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "MMMM"
        let month = formatter.string(from: Date())
        let predicate = NSPredicate(format: "label MATCHES %@", ".*\\b\(day)\\b.*")
        var listed: [String] = []
        let deadline = Date().addingTimeInterval(TimeInterval(timeout))
        repeat {
            let inPicker = app.datePickers.buttons.matching(predicate).allElementsBoundByIndex
            let window = app.windows.firstMatch.frame
            let pickerDays = inPicker.filter { element in
                let f = element.frame
                return element.exists && !f.isEmpty && f.minX >= 0 && f.maxX <= window.width
                    && f.minY >= 0 && f.maxY <= window.height
            }
            let new = dayButtons(day).filter { !excluding.contains("\($0.label)@\($0.frame)") }
            let onScreen = pickerDays.isEmpty ? new : pickerDays
            if let best = onScreen.first(where: { $0.label.contains(month) }) ?? onScreen.first { return best }
            listed = (inPicker + new).map { "\($0.label) @ \($0.frame)" }
            usleep(500_000)
        } while Date() < deadline
        let detail = String(listed.joined(separator: "; ").prefix(450))
        let opened = calendarPopoverOpen ? "open" : "not open"
        throw StepFailure(description: "No new calendar day \(day) on screen (\(month), the popover \(opened)); candidates: \(detail)")
    }

    /// Whether the compact date picker's calendar is open: its month arrows show. The form's pills
    /// have none, and no other calendar (Jump to date's sheet) is up during the form's steps.
    private var calendarPopoverOpen: Bool {
        app.buttons["DatePicker.NextMonth"].exists
    }

    /// Closes the compact date picker's calendar, if it is up, by a tap outside it: on the dismiss
    /// region where it is named, else on the sheet's bar title, outside the calendar (a tap there
    /// does nothing if it reaches the form), else at the top of the screen.
    private func closeCalendarPopover() {
        guard calendarPopoverOpen else { return }
        let outside = app.otherElements["PopoverDismissRegion"]
        if outside.exists {
            outside.tap()
        } else {
            let calendar = app.datePickers.allElementsBoundByIndex.map(\.frame).max { $0.height < $1.height } ?? .zero
            let title = app.navigationBars.staticTexts.firstMatch
            if title.exists && !title.frame.intersects(calendar) {
                title.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
            } else {
                app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.12)).tap()
            }
        }
        _ = app.buttons["DatePicker.NextMonth"].waitForNonExistence(timeout: 5)
    }

    /// What a failed step may leave over the next one, closed choosing nothing: the date picker's
    /// calendar, the Repeat menu (a tap outside), an action sheet (its Cancel), "Discard changes?"
    /// (Discard: the step that asked has failed), and any sheet still up (swiped down from its
    /// grabber). Run 13 left the event preview up after one failed swipe, and the five steps after
    /// it failed only because the tab bar was under it.
    private func closeTransients() {
        closeCalendarPopover()
        if app.buttons["Weekly"].exists && app.buttons["Monthly"].exists { tapOutside() }
        let sheetCancel = app.sheets.buttons["Cancel"]
        if sheetCancel.exists { sheetCancel.tap() }
        if app.staticTexts["Discard changes?"].exists { pressIfThere("Discard") }
        for _ in 0..<3 {
            guard let grabber = sheetGrabber() else { break }
            grabber.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
                .press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.98)))
            sleep(1)
            if app.staticTexts["Discard changes?"].exists { pressIfThere("Discard") }
        }
    }

    /// The event form's Repeat row ends in the system's pop-up button (MON-24 phase 3, part C,
    /// Task 4), not a row that opens a dialog: VoiceOver reads it as "Repeat, <value>", its menu
    /// lists the options, and the one picked is what the button then reads. The labels are the
    /// shared strings in English ("Weekly" is `event_repeat_weekly`).
    private func repeatMenu() throws {
        let sheetBar = app.navigationBars["New event"]
        if !sheetBar.exists { try eventFormSheet() }
        dismissKeyboard()
        let repeatButton = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Repeat,")).firstMatch
        // A native control below the form's viewport (under a keyboard that stayed up) is not in the
        // tree until it is scrolled into view.
        if !repeatButton.waitForExistence(timeout: 5) || repeatButton.frame.maxY > app.windows.firstMatch.frame.maxY - 160 {
            try bringIntoForm(repeatButton, what: "Repeat's pop-up button")
        }
        try require(repeatButton.waitForExistence(timeout: timeout), "Repeat has no pop-up button named \"Repeat, …\"")
        sleep(1)
        press(repeatButton)
        let weekly = app.buttons.matching(NSPredicate(format: "label == %@", "Weekly")).firstMatch
        try require(weekly.waitForExistence(timeout: timeout), "Repeat did not open a menu with \"Weekly\"")
        try require(app.buttons["Monthly"].exists, "The Repeat menu does not list every option")
        try require(!app.alerts.firstMatch.exists, "Repeat opened a dialog as well as the menu")
        shot("10e-repeat-menu")
        weekly.tap()
        try require(app.buttons["Monthly"].waitForNonExistence(timeout: timeout), "The Repeat menu stayed open after a pick")
        let picked = app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@ AND label CONTAINS %@", "Repeat,", "Weekly")
        ).firstMatch
        try require(picked.waitForExistence(timeout: timeout), "The Repeat button does not read \"Weekly\" after the pick")
        shot("10f-repeat-picked")
    }

    /// "Jump to date", opened from the month title on the calendar's card, is the system's inline
    /// calendar in a sheet (MON-24 phase 3, part C, Task 3). A swipe down closes it without moving
    /// the calendar, leaves nothing presented, and it opens again.
    private func jumpToDateSwipeDown() throws {
        // The tab bar is under a sheet still up, so a tab tap would not reach the calendar: close it.
        let eventSheet = app.navigationBars["New event"]
        if eventSheet.exists {
            dismissKeyboard()
            swipeSheetDown(from: eventSheet)
            try require(eventSheet.waitForNonExistence(timeout: timeout), "The event form did not close before the jump")
        }
        try goToCalendarRoot()
        try require(calendarHeading.waitForExistence(timeout: timeout), "The calendar's first screen is missing")
        let title = calendarMonthButton
        try require(title.waitForExistence(timeout: timeout), "The card's month title is not a button")

        press(title)
        let sheetBar = app.navigationBars.containing(NSPredicate(format: "label == %@", "Cancel")).firstMatch
        try require(sheetBar.waitForExistence(timeout: timeout), "Jump to date did not open as a sheet with Cancel")
        try require(app.datePickers.firstMatch.exists, "The jump-to-date sheet holds no date picker")
        shot("17b-jump-to-date-sheet")
        swipeSheetDown(from: sheetBar)
        try require(sheetBar.waitForNonExistence(timeout: timeout), "A swipe down did not close the jump-to-date sheet")
        try require(title.waitForExistence(timeout: timeout), "The calendar moved after a dismissed jump")
        try require(app.navigationBars.count == 0, "Something was left presented over the calendar")

        press(title)
        try require(sheetBar.waitForExistence(timeout: timeout), "Jump to date did not open a second time")
        press(sheetBar.buttons.matching(NSPredicate(format: "label == %@", "Cancel")).firstMatch)
        try require(sheetBar.waitForNonExistence(timeout: timeout), "Cancel did not close the jump-to-date sheet")
        try require(title.exists, "The calendar moved after a cancelled jump")
    }

    /// Deleting an event asks first, in the system's action sheet (MON-24 phase 3, part C, Task 5):
    /// "Delete event" over a red Delete and a separate Cancel. It saves an event of its own, opens
    /// it from the calendar into its form, asks to delete it from the form's bar, and cancels: the
    /// form stays open, and once closed the event is still on the calendar.
    private func deleteEventAsks() throws {
        try goToCalendarRoot()
        press(try element(labelled: "Add event", in: app.buttons))
        let newBar = app.navigationBars["New event"]
        try require(newBar.waitForExistence(timeout: timeout), "The event form did not open")
        // The new event's draft may still hold the earlier steps' title; the check's words are
        // appended to it, and everything below looks the event up by what its label contains.
        try type(deleteCheckTitle, into: field(labelled: "Event title"))
        dismissKeyboard()
        let save = newBar.buttons["Save event"]
        try require(waitUntilEnabled(save), "Save stayed disabled once the event had a title")
        press(save)
        try require(newBar.waitForNonExistence(timeout: timeout), "Saving did not close the event form")

        // A tap on the month cell opens Day view; a tap on the event there opens its preview.
        let edit = app.buttons.matching(NSPredicate(format: "label == %@", "Edit")).firstMatch
        for _ in 0..<3 where !edit.exists {
            guard let event = tourTopmost(containing: deleteCheckTitle) else {
                throw StepFailure(description: "Nothing on the calendar reads \"\(deleteCheckTitle)\"")
            }
            press(event)
            _ = edit.waitForExistence(timeout: 5)
        }
        try require(edit.exists, "The saved event's preview did not open")
        press(edit)
        let editBar = app.navigationBars["Edit event"]
        try require(editBar.waitForExistence(timeout: timeout), "Edit did not open the event's form")

        press(try element(labelled: "Delete event", in: editBar.buttons))
        let sheet = app.sheets.firstMatch
        try require(sheet.waitForExistence(timeout: timeout), "Delete did not ask in the system's action sheet")
        try require(!app.alerts.firstMatch.exists, "Delete asked in an alert, not an action sheet")
        try require(sheet.staticTexts["Delete event"].exists, "The action sheet is not titled Delete event")
        try require(sheet.buttons["Delete"].exists, "The action sheet has no Delete")
        let cancel = sheet.buttons["Cancel"]
        try require(cancel.exists, "The action sheet has no Cancel")
        shot("17c-delete-event-action-sheet")
        cancel.tap()
        try require(sheet.waitForNonExistence(timeout: timeout), "Cancel left the action sheet up")
        try require(editBar.exists, "Cancel closed the event's form")

        closeButton(of: editBar).tap()
        try require(editBar.waitForNonExistence(timeout: timeout), "The event's form did not close")
        try require(
            element(labelledContaining: deleteCheckTitle).waitForExistence(timeout: timeout),
            "The event was deleted although the parent cancelled"
        )
    }

    /// An event's preview is the system's own sheet at half the screen (MON-24 phase 3, part C,
    /// Task 6): opened from the month grid, it shows the event's title in the lower half while the
    /// calendar above it stays in reach (the Month/Week/Day control can still be touched). A swipe
    /// down closes it and leaves nothing presented, and the event opens it again. It uses the event
    /// `deleteEventAsks` saved and kept.
    private func eventPreviewSheet() throws {
        try goToCalendarRoot()
        // The step before left Day view open (opened from a month cell): a tap on the Calendar tab
        // opens Month on today (`CalendarTabTaps`), and its bar, which Day view's Back needed, goes.
        // The view button reads the view: "View: Month".
        let month = viewButton(reading: "Month")
        // The shell logs what it saw of the tap (COPLANLY_TABTAP); the job prints those lines.
        try require(month.waitForExistence(timeout: timeout), "A tap on the Calendar tab did not return Day view to Month")
        try require(waitForNoNavigationBar(), "The Calendar's Month still shows a navigation bar")
        let edit = app.buttons.matching(NSPredicate(format: "label == %@", "Edit")).firstMatch
        // A failure leaves no preview over the tab bar, so the steps after it start from a tab.
        do {
            try previewChecks(edit: edit)
        } catch {
            // The sheet leaves the calendar above it undimmed and in reach, so it is swiped away, not
            // tapped outside: a tap there would land on the calendar.
            if edit.exists, let title = try? previewTitle(above: edit) { swipePreviewDown(from: title) }
            _ = edit.waitForNonExistence(timeout: 5)
            throw error
        }
    }

    /// Waits a little for no navigation bar to be drawn (one leaving may still be animating away).
    private func waitForNoNavigationBar() -> Bool {
        let noBar = XCTNSPredicateExpectation(predicate: NSPredicate(format: "count == 0"), object: app.navigationBars)
        return XCTWaiter().wait(for: [noBar], timeout: 10) == .completed
    }

    /// The preview's checks: opened from the month grid (through Day view, whose bar carries Back
    /// and leaves the header's Month/Week/Day control below it), swiped away, and opened again.
    private func previewChecks(edit: XCUIElement) throws {
        try openPreview(of: deleteCheckTitle, edit: edit)

        let title = try previewTitle(above: edit)
        let control = viewButton
        try require(control.exists, "The calendar's view button is not above the preview sheet")
        try require(control.frame.maxY < title.frame.minY, "The preview sheet covers the calendar's top")
        shot("17d-event-preview-sheet")
        // In reach means a tap on it works: `isHittable` reads false for every element under an
        // undimmed sheet's pass-through container (run 10), so the button is tapped where it is
        // drawn, its menu must open and Week must then be the view under the sheet.
        control.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        let week = app.buttons.matching(NSPredicate(format: "label == %@", "Week")).firstMatch
        try require(week.waitForExistence(timeout: timeout), "The calendar above the preview sheet is out of reach")
        week.tap()
        try require(viewButton(reading: "Week").waitForExistence(timeout: timeout), "Week did not take over the calendar")
        shot("17d2-event-preview-sheet-week-tapped")
        // Looked up again: the calendar under the sheet changed, and with it the elements' order.
        // (The week's events load anew, so the sheet may have gone and come back meanwhile.)
        _ = edit.waitForExistence(timeout: 5)
        if edit.exists { swipePreviewDown(from: try previewTitle(above: edit)) }
        try require(edit.waitForNonExistence(timeout: timeout), "A swipe down did not close the event's preview")
        // The sheet covers the tab bar while it is up, so a tab bar within reach means it has gone.
        let calendarTab = app.tabBars.firstMatch.buttons["Calendar"]
        try require(calendarTab.isHittable, "Something was left presented over the calendar")

        try openPreview(of: deleteCheckTitle, edit: edit)
        shot("17e-event-preview-sheet-again")
        swipePreviewDown(from: try previewTitle(above: edit))
        try require(edit.waitForNonExistence(timeout: timeout), "The second preview did not close")
        try require(calendarTab.isHittable, "Something was left presented after the second preview")
    }

    /// The event's title in the preview sheet: the highest label holding it below the sheet's top
    /// (its grabber, or 40 % down the screen) and above the sheet's [edit] button. The highest, not
    /// the lowest: the sheet's title sits right under its grabber, while the calendar under the
    /// undimmed sheet may draw the event lower down (run 13, Week view's block).
    private func previewTitle(above edit: XCUIElement) throws -> XCUIElement {
        let titles = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", deleteCheckTitle))
        let sheetTop = sheetGrabber()?.frame.maxY ?? app.frame.height * 0.4
        let inSheet = titles.allElementsBoundByIndex
            .filter { $0.frame.minY > sheetTop && $0.frame.minY < edit.frame.minY }
            .min { $0.frame.minY < $1.frame.minY }
        guard let found = inSheet else {
            throw StepFailure(description: "The preview sheet does not show the event's title in the lower half")
        }
        return found
    }

    /// Drags the preview sheet down to the bottom of the screen from its grabber, the system's own
    /// handle, or, where none is reported, from just above its [title], inside the sheet's top
    /// margin. Run 13 dragged from above a "title" that was the Week view's block of the event under
    /// the sheet, a point inside the sheet's content, which scrolled rather than closed it.
    private func swipePreviewDown(from title: XCUIElement) {
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.98))
        if let grabber = sheetGrabber() {
            grabber.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).press(forDuration: 0.05, thenDragTo: end)
            return
        }
        let start = app.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: app.frame.width / 2, dy: title.frame.minY - 12))
        start.press(forDuration: 0.05, thenDragTo: end)
    }

    /// The grabber of the sheet on top (the lowest one drawn), if UIKit reports one.
    private func sheetGrabber() -> XCUIElement? {
        app.buttons.matching(NSPredicate(format: "label == %@", "Sheet Grabber")).allElementsBoundByIndex
            .filter { $0.exists && !$0.frame.isEmpty }
            .max { $0.frame.minY < $1.frame.minY }
    }

    /// Opens [title]'s preview from the calendar: a tap on its month cell opens Day view, and a tap
    /// on the event there opens the preview, whose [edit] button then shows.
    private func openPreview(of title: String, edit: XCUIElement) throws {
        for _ in 0..<3 where !edit.exists {
            guard let event = tourTopmost(containing: title) else {
                throw StepFailure(description: "Nothing on the calendar reads \"\(title)\"")
            }
            press(event)
            _ = edit.waitForExistence(timeout: 5)
        }
        try require(edit.exists, "The event's preview did not open")
    }

    /// The title of the event `deleteEventAsks` saves and then does not delete.
    private let deleteCheckTitle = "Shell delete check"

    /// A new event asks nothing when it is left: its draft is kept as the parent types and comes
    /// back on the next "+" (CLAUDE.md design item 16), so a swipe down closes the sheet at once.
    private func newEventSwipeDown() throws {
        let sheetBar = app.navigationBars["New event"]
        if !sheetBar.exists { try eventFormSheet() }
        swipeSheetDown(from: sheetBar)
        try require(sheetBar.waitForNonExistence(timeout: timeout), "A swipe down did not close the new event's sheet")
        try require(!app.staticTexts["Discard changes?"].exists, "A new event asked before closing")
        try require(calendarHeading.waitForExistence(timeout: timeout), "The calendar did not come back after the event form")
        try require(app.navigationBars.count == 0, "A bar was left over the calendar after the event form")
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
        let text = "Keyboard check"
        try type(text, into: description)
        try require(app.keyboards.firstMatch.waitForExistence(timeout: timeout), "No keyboard over the description")
        shot("18-event-description-keyboard")
        try require(isClearOfKeyboard(description), "The description field is under the keyboard")
        try require(sheetBar.buttons["Save event"].exists, "The sheet's Save left its bar while typing")
        // What was typed is deleted again: a new event's draft is kept as the parent types (CLAUDE.md
        // design item 16), and a check's words must not come back on the next "+".
        app.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: text.count))
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
        let code = "ABC234"
        // XCUITest relaunches the app to open a link (a new process), so this is the cold start's
        // path: the link waits for the signed-in tabs and then pushes Pairing onto the tab shown.
        app.open(URL(string: "coplanly://pair?code=\(code)")!)
        let answer = app.descendants(matching: .any).matching(NSPredicate(
            format: "label CONTAINS %@ OR label CONTAINS %@", "No invitation matches that code", "Link with"
        )).firstMatch
        let paired = element(labelledContaining: "Add another co-parent")
        // The refusal is a snackbar of a few seconds, which a snapshot taken while it animates can
        // miss: it is looked for until Pairing has been up a while, and then the code is looked for
        // where it stays, in the screen's own code field.
        var reached = false
        var pairingSince: Date?
        let deadline = Date().addingTimeInterval(120)
        while !reached && Date() < deadline {
            reached = answer.exists
            if !reached && pairingSince == nil && paired.exists { pairingSince = Date() }
            if let since = pairingSince, Date().timeIntervalSince(since) > 15 { break }
        }
        shot("16-pairing-link")
        if !reached && paired.exists { reached = codeInField(code, under: paired) }
        try require(reached, "The link did not reach the pairing screen with its code")
        if app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "Link with")).firstMatch.exists {
            pressIfThere("Cancel")
        }
        try goToFamilyRoot()
    }

    /// Whether Pairing holds [code] after a link: its "Enter a code" field, opened from [row] ("Add
    /// another co-parent"), shows it, or the field's refusal of it shows under it.
    private func codeInField(_ code: String, under row: XCUIElement) -> Bool {
        press(row)
        let enter = app.buttons["Enter a code"]
        guard enter.waitForExistence(timeout: timeout) else { return false }
        press(enter)
        let holding = NSPredicate(format: "value CONTAINS %@ OR label CONTAINS %@", code, "No invitation matches that code")
        let field = app.textViews.matching(holding).firstMatch
        let plain = app.textFields.matching(holding).firstMatch
        let refusal = element(labelledContaining: "No invitation matches that code")
        for _ in 0..<20 {
            if field.exists || plain.exists || refusal.exists { return true }
            usleep(500_000)
        }
        return false
    }

    /// The vault's Add a file presents the system's document picker (MON-24 phase 3, part C,
    /// Task 11), and its Cancel takes it away and leaves Documents as it was, picking nothing; a
    /// second tap opens it again. The simulator has no camera, so Take a photo is not offered.
    private func documentPicker() throws {
        try goToFamilyRoot()
        let row = try element(containing: "Court orders, school letters")
        let screenBottom = app.windows.firstMatch.frame.maxY - 120
        for _ in 0..<6 where row.frame.maxY > screenBottom {
            app.swipeUp()
        }
        sleep(1)
        press(row)
        if !app.navigationBars["Family"].waitForNonExistence(timeout: 3) { press(row) }
        try require(app.navigationBars["Documents"].waitForExistence(timeout: timeout), "Documents did not open")
        try require(!app.buttons["Take a photo"].exists, "Take a photo was offered on a phone with no camera")
        for attempt in 1...2 {
            press(try element(labelled: "Add a file", in: app.buttons))
            let cancel = app.buttons["Cancel"]
            try require(cancel.waitForExistence(timeout: timeout), "The document picker did not appear (open \(attempt))")
            if attempt == 1 { shot("17-document-picker") }
            cancel.tap()
            try require(cancel.waitForNonExistence(timeout: timeout), "Cancel did not close the document picker")
            try require(app.navigationBars["Documents"].waitForExistence(timeout: timeout), "Cancel left the Documents screen")
        }
        try goToFamilyRoot()
    }

    /// The pairing screen's Share, under "Add another co-parent", presents the system's share sheet
    /// with the invitation (MON-24 phase 3, part C, Task 11), and its Close takes it away and leaves
    /// the pairing screen as it was. Opening the row mints a code on the emulators.
    private func inviteShareSheet() throws {
        try goToFamilyRoot()
        let row = try element(containing: "Linked: ")
        press(row)
        if !app.navigationBars["Family"].waitForNonExistence(timeout: 3) { press(row) }
        press(try element(containing: "Add another co-parent"))
        // The code card's button is `pairing_share_link`, "Share link" in English.
        let share = app.buttons.matching(NSPredicate(format: "label == %@", "Share link")).firstMatch
        try require(share.waitForExistence(timeout: 60), "The invitation's Share link button did not appear")
        // Since phase 4 part E1 the card draws the invitation's QR above it, which can push the button
        // below the fold, where a tap lands off screen: scroll it into view first.
        let screenBottom = app.windows.firstMatch.frame.maxY - 120
        for _ in 0..<6 where share.frame.maxY > screenBottom {
            app.swipeUp()
        }
        sleep(1)
        press(share)
        let sheet = app.otherElements["ActivityListView"]
        let copy = app.buttons["Copy"]
        var shown = false
        for _ in 0..<30 where !shown {
            shown = sheet.exists || copy.exists
            if !shown { sleep(1) }
        }
        shot("18-invite-share-sheet")
        try require(shown, "The share sheet did not appear")
        let close = app.buttons["Close"]
        let tappedClose = close.exists
        if tappedClose {
            close.tap()
        }
        // A share sheet with no Close button is swiped down. The drag starts on the sheet's own top edge,
        // since a fixed point on the screen can fall on the dimmed area above a half-height sheet, where
        // a drag closes nothing. A tap above the sheet is the last way out, after two swipes.
        var closed = sheet.waitForNonExistence(timeout: tappedClose ? timeout : 2) && !copy.exists
        for attempt in 0..<3 where !closed {
            let window = app.windows.firstMatch.frame
            let top = sheet.exists ? sheet.frame.minY : window.height * 0.5
            if attempt < 2 {
                let from = app.coordinate(withNormalizedOffset: .zero)
                    .withOffset(CGVector(dx: window.midX, dy: top + 12))
                let to = app.coordinate(withNormalizedOffset: .zero)
                    .withOffset(CGVector(dx: window.midX, dy: window.maxY - 4))
                from.press(forDuration: 0.05, thenDragTo: to)
            } else {
                app.coordinate(withNormalizedOffset: .zero)
                    .withOffset(CGVector(dx: window.midX, dy: max(top * 0.3, 60)))
                    .tap()
            }
            closed = sheet.waitForNonExistence(timeout: 10) && !copy.exists
        }
        try require(closed, "The share sheet did not close")
        try require(labelExists(containing: "Add another co-parent"), "Closing the share sheet left the pairing screen")
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
        // The confirmation is the system's action sheet (a destructive question, design item 1),
        // whose button reads as the row does. It is tapped in the sheet, once it can be touched: an
        // index among every "Sign out of app" could name the row under the sheet, and a tap there
        // lands on the sheet's dimming and cancels it.
        let confirm = try signOutConfirmation()
        confirm.tap()
        try require(app.staticTexts["Welcome back!"].waitForExistence(timeout: 120), "Signing out did not reach the sign-in screen")
        try require(!app.tabBars.firstMatch.exists, "The tab bar is still there on the sign-in screen")
        shot("11-signed-out")
        // The sign-out ran to its end, not only to the screen change: the session is gone from the
        // Keychain too, so a relaunch opens on the sign-in screen again (Settings signs out through
        // the shell's own session state, which outlives the Settings screen it was started from).
        app.terminate()
        app.launch()
        try require(app.staticTexts["Welcome back!"].waitForExistence(timeout: 120), "A relaunch after signing out was still signed in")
        try require(!app.tabBars.firstMatch.exists, "A relaunch after signing out showed the tabs")
        try signIn(email: email, password: password)
        // Fresh tabs: the Family tab opens at its first screen, not at the Settings it was left on.
        app.tabBars.firstMatch.buttons["Family"].tap()
        try require(app.navigationBars["Family"].waitForExistence(timeout: timeout), "The Family tab did not open at its first screen")
        try require(!app.navigationBars["Settings"].exists, "The Family tab kept the signed-out session's Settings")
        shot("12-fresh-tabs")
    }

    /// The sign-out question's confirming button: in the system's action sheet, or an alert should
    /// a release draw the question as one, waited for until it can be touched.
    private func signOutConfirmation() throws -> XCUIElement {
        let byLabel = NSPredicate(format: "label == %@", "Sign out of app")
        let inSheet = app.sheets.buttons.matching(byLabel).firstMatch
        let inAlert = app.alerts.buttons.matching(byLabel).firstMatch
        let deadline = Date().addingTimeInterval(TimeInterval(timeout))
        repeat {
            for button in [inSheet, inAlert] where button.exists && button.isHittable { return button }
            usleep(500_000)
        } while Date() < deadline
        throw StepFailure(description: "No sign-out confirmation in an action sheet or an alert")
    }

    /// Waits up to two minutes for a relaunched app to show its Family tab. A snapshot taken while
    /// the starting app keeps its main thread busy fails after 30 s with "Timed out while evaluating
    /// UI query", which XCUITest does not report as a false answer but as a failure that ends the
    /// test at once (the walk's summary was then never printed). So no element is asked for until
    /// the app runs in the foreground and has had a moment to start; `app.state` needs no snapshot.
    private func waitForRelaunchedTabs() -> Bool {
        let deadline = Date().addingTimeInterval(120)
        guard app.wait(for: .runningForeground, timeout: 60) else { return false }
        sleep(15)
        let family = app.tabBars.buttons["Family"]
        // Bounded: two minutes in all, each wait 10 s, and over at once if the app stopped.
        repeat {
            guard app.state == .runningForeground else { return false }
            if family.waitForExistence(timeout: 10) { return true }
            sleep(2)
        } while Date() < deadline
        return false
    }

    /// Settings → Delete account → both confirmations (MON-24 phase 3, part B, Task 11): the
    /// `deleteAccount` callable erases the account on the emulators, the phone is wiped and signed
    /// out, and the app shows the sign-in screen with no tabs. The labels are the shared strings in
    /// English.
    private func deleteAccount() throws {
        app.terminate()
        app.launch()
        try require(waitForRelaunchedTabs(), "No tab bar after relaunching")
        try settings()
        let row = try element(labelled: "Delete account", in: app.buttons)
        let screenBottom = app.windows.firstMatch.frame.maxY - 120
        for _ in 0..<12 where row.frame.maxY > screenBottom {
            app.swipeUp()
        }
        sleep(1)
        press(row)
        try require(element(labelledContaining: "Delete this account?").waitForExistence(timeout: timeout), "No first confirmation")
        shot("20-delete-account-question")
        // The confirmation's button reads as the row does; it is the last one drawn.
        let named = app.buttons.matching(NSPredicate(format: "label == %@", "Delete account"))
        sleep(1)
        press(named.element(boundBy: max(named.count - 1, 0)))
        let permanently = app.buttons.matching(NSPredicate(format: "label == %@", "Delete permanently")).firstMatch
        try require(permanently.waitForExistence(timeout: timeout), "No final confirmation")
        shot("21-delete-account-final-question")
        press(permanently)
        try require(app.staticTexts["Welcome back!"].waitForExistence(timeout: 120), "Deleting the account did not reach the sign-in screen")
        try require(!app.tabBars.firstMatch.exists, "The tab bar is still there after deleting the account")
        shot("22-account-deleted")
    }

    // MARK: - Getting to a known place

    private func goToHomeRoot() throws {
        let home = app.tabBars.firstMatch.buttons["Home"]
        home.tap()
        // A pushed screen shows a bar, Home's first screen none: a second tap pops to it.
        if app.navigationBars.firstMatch.waitForExistence(timeout: 3) { home.tap() }
        try requireHeaderRoot("Home")
    }

    /// The Calendar tab's first screen, which shows no system bar (MON-24 phase 3, part C, Task 8).
    private func goToCalendarRoot() throws {
        let calendar = app.tabBars.firstMatch.buttons["Calendar"]
        calendar.tap()
        try require(calendar.waitForExistence(timeout: timeout) && calendar.isSelected, "The Calendar tab did not select")
    }

    /// Data sources' title, which its system bar shows (MON-24 phase 3, part B, Task 6).
    private let dataSourcesTitle = "Data sources and licences"

    /// Opens Data sources from Settings: a sub-screen under its own system bar, with the back button.
    private func openDataSources() throws {
        try openFromSettings(dataSourcesTitle)
    }

    /// Sign-in methods' title, its Settings row's and its system bar's (MON-24 phase 4, part A).
    private let signInMethodsTitle = "Sign-in methods"

    /// Settings → Sign-in methods (MON-24 phase 4, part A, Task 6): the screen opens under its own
    /// system bar and lists the e-mail the walk signed in with, then Back returns to Settings.
    private func signInMethods() throws {
        if !app.navigationBars["Settings"].exists { try settings() }
        try openFromSettings(signInMethodsTitle)
        let email = element(labelledContaining: "E-mail")
        try require(email.waitForExistence(timeout: timeout), "Sign-in methods lists no e-mail row")
        // The e-mail the walk signed in with is the one method this account holds (Google needs a
        // bundled plist, Apple is not linked), so the e-mail row itself must read "Linked": its own
        // label when the row is one element, else its status line, the exact "Linked" ("Not linked"
        // does not count) on the same row, within 30 pt of the title (a row is 50 pt or more).
        let emailLinked = email.label.contains("Linked") ||
            app.descendants(matching: .any)
                .matching(NSPredicate(format: "label == %@", "Linked"))
                .allElementsBoundByIndex
                .contains { abs($0.frame.midY - email.frame.midY) < 30 }
        try require(emailLinked, "Sign-in methods does not show the e-mail row as linked")
        shot("09b-sign-in-methods")
        app.navigationBars[signInMethodsTitle].buttons.element(boundBy: 0).tap()
        try require(app.navigationBars["Settings"].waitForExistence(timeout: timeout), "Back from Sign-in methods did not return to Settings")
    }

    /// Opens Settings' row titled [title]: a sub-screen under its own system bar of that title, with
    /// the back button.
    private func openFromSettings(_ title: String) throws {
        let row = try element(containing: title)
        let screenBottom = app.windows.firstMatch.frame.maxY - 120
        for _ in 0..<6 where row.frame.maxY > screenBottom {
            app.swipeUp()
        }
        // A tap while the list still glides after a swipe only stops it: let it settle, and tap
        // once more if the first tap was spent that way.
        sleep(1)
        press(row)
        if !app.navigationBars["Settings"].waitForNonExistence(timeout: 3) { press(row) }
        sleep(2)
        try require(!app.navigationBars["Settings"].exists, "\(title) did not open over Settings")
        try require(app.navigationBars[title].waitForExistence(timeout: timeout), "\(title) has no system bar")
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
            // After the record, so it shows what the step left; the next step then starts clean.
            closeTransients()
        }
        outcomes.append((name, outcome))
        // As it goes too, for a walk that XCUITest ends before its summary.
        print("COPLANLY_STEP_DONE \(name): \(outcome)")
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
    /// On a simulator booted moments before, the first tap can land before the field takes focus,
    /// and typing then fails with "Neither element nor any descendant has keyboard focus" (CI, 23
    /// November 2026): the tap is repeated until the field holds the focus, three times at most.
    private func type(_ text: String, into field: XCUIElement) throws {
        let focused = NSPredicate(format: "hasKeyboardFocus == true")
        for _ in 0..<3 {
            press(field)
            _ = app.keyboards.firstMatch.waitForExistence(timeout: 5)
            if app.descendants(matching: .any).matching(focused).firstMatch.waitForExistence(timeout: 5) { break }
        }
        app.typeText(text)
    }

    /// Closes the keyboard with its own key, so what it covered can be tapped.
    private func dismissKeyboard() {
        let keyboard = app.keyboards.firstMatch
        guard keyboard.exists else { return }
        app.typeText("\n")
        _ = keyboard.waitForNonExistence(timeout: 5)
    }

    /// The first element of [query] that can be touched, waiting a little for one: a query may also
    /// match elements kept off screen (a calendar's neighbouring months), which a tap cannot reach.
    private func hittable(in query: XCUIElementQuery, what: String) throws -> XCUIElement {
        for _ in 0..<10 {
            let candidates = query.allElementsBoundByIndex
            if let match = candidates.first(where: { $0.exists && $0.isHittable }) { return match }
            usleep(500_000)
        }
        throw StepFailure(description: "Nothing on screen can be touched for \(what)")
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

    // MARK: - The tour

    /// Whether `testTour` is running: XCUITest's issues then skip a destination instead of failing.
    private var touring = false

    /// The first issue XCUITest recorded while the tour visited the current destination.
    private var tourIssue: String?

    /// Each destination's outcome, in the order visited: "reached" or "skipped: …".
    private var tourOutcomes: [(route: String, outcome: String)] = []

    /// The destinations during which the app stopped running.
    private var tourCrashes: [String] = []

    /// The appearance `testTourAppearance` shoots under (`dark`, `contrast`, `large`), which its
    /// screenshots' names carry after `tour-`; null for `testTour`.
    private var tourVariant: String?

    /// When the tour stops visiting: the visits left are skipped with that reason, so the tour
    /// always reaches its summary inside CI's step limit (50 minutes) even when every visit misses.
    private var tourDeadline = Date.distantFuture

    /// The budget [tourDeadline] was set from, in seconds, for the message of a visit it skipped:
    /// `tourBudget` for `testTour`, `appearanceBudget` for `testTourAppearance`.
    private var activeTourBudget: TimeInterval = 0

    /// The tour's own time budget, from its first visit.
    private let tourBudget: TimeInterval = 35 * 60

    /// The part of `testTour` this run takes, from `COPLANLY_TOUR_SHARD` ("1/2", "2/2"): CI runs the
    /// halves on two runners at once (CI speed, 24 November 2026). The visits are dealt out in turn,
    /// in the order `tourSignedIn` makes them, and every part signs in itself. Nil runs every visit.
    private var tourShard: (index: Int, count: Int)?

    /// How many visits the tour has dealt out so far, to [tourShard]'s parts.
    private var tourVisitsDealt = 0

    /// The title of the event `tools/ios/seed-shell-family.js --tour` writes for today.
    private let tourEventTitle = "Tour dentist"

    /// Every route of `AppDestinationCatalog.all` (shared-ui's `navigation/AppDestination.kt`), in
    /// its order. Keep the two lists alike: a route the tour has no way to is reported as such, so
    /// a new destination shows in `tour.txt` the day it is added. A route holds no space, which
    /// `simulator.sh`'s `tour.txt` grep (`COPLANLY_TOUR [^ ]+: `) relies on.
    private let tourRoutes = [
        "loading", "privacy_consent", "onboarding", "auth", "home", "calendar",
        "add_event?date={date}&hour={hour}", "edit_event/{eventId}", "contacts",
        "change_requests?eventId={eventId}&section={section}", "request_change/{eventId}", "settings",
        "parenting_plan", "export?thread={thread}&family={family}", "month_review", "unassigned_records",
        "family_documents?family={family}", "support", "earlier_families", "earlier_family/{familyId}",
        "journal", "stickers", "journal_entry/{entryId}", "child_info", "child_detail/{childInfoId}",
        "edit_child_info/{childInfoId}", "pets", "pet_detail/{petId}", "edit_pet/{petId}",
        "pairing?code={code}&enter={enter}&unpair={unpair}", "guest_accept?code={code}", "custody_conflict",
        "school_import", "school_connect?connectionId={connectionId}", "calendar_feed", "data_sources",
        "friends", "friend_detail/{friendUid}", "friend_profile", "friend_calendar/{familyId}",
        "professionals", "professional_calendar/{grantId}", "professional_plan/{grantId}",
        "custody_setup?planQuestion={planQuestion}", "my_profile", "sign_in_methods", "coparent_profile",
        "conversations?draft={draft}", "chat/{conversationId}?draft={draft}", "family", "expenses",
        "add_expense", "edit_expense/{expenseId}",
    ]

    /// The system's controls the tour shows in use (MON-24 phase 3, part C), each a visit of its own
    /// after the destinations, shot as `tour-control-…`. They are not routes: the summary counts
    /// them apart, and one the tour cannot show is skipped with the reason, as a route is.
    private let tourControls = [
        "control-switch", "control-view-menu", "control-date-sheet", "control-option-menu",
        "control-action-sheet", "control-preview-sheet", "control-paste",
    ]

    /// The destinations no signed-in parent of the seeded family can reach, each with the reason.
    private let tourUnreachable: [String: String] = [
        "loading": "shown only until the start destination is known, a moment no tap leads to",
        "privacy_consent": "the iPhone collects no telemetry, so the question is never asked (spec §5.2)",
        "onboarding": "the seeded parent finished onboarding; a new account would need the wizard's own walk",
        "unassigned_records": "its row is drawn only while records of no family wait, and every seeded record names the family",
        "earlier_families": "its row is drawn only after a family ended, and the seeded parent has one live family",
        "earlier_family/{familyId}": "opened from Earlier families, which the seeded parent does not have",
        "custody_conflict": "opened only by a pairing whose two schedules disagree; the seeded pair has none",
        "friend_detail/{friendUid}": "opened from a family friend's grant; no friend was admitted (that needs a third account and the friend callable)",
        "friend_profile": "a family friend's own screen, for a friend's account, not a parent's",
        "friend_calendar/{familyId}": "a family friend's view of a family, for a friend's account, not a parent's",
        "professional_calendar/{grantId}": "a professional's view of a family, for a professional's account",
        "professional_plan/{grantId}": "a professional's view of the plan, for a professional's account",
        "chat/{conversationId}?draft={draft}": "with one conversation the Chat tab renders the thread in place (design item 7), shot as conversations",
    ]

    /// The tour (MON-24 phase 3, part B, Task 13): signs in as the first parent of the family
    /// `tools/ios/seed-shell-family.js --tour` seeded on fresh emulators (an event today with a
    /// change request about it, a child, a pet, an expense and a vault document), visits every
    /// destination it can reach from there and attaches a screenshot named `tour-<route>` for each,
    /// the route as `AppDestinationCatalog` writes it up to its first `/` or `?`. Since part C it also
    /// shows the system's controls in use (`tourControls`: a switch, a segment, the date sheet, an
    /// option menu, an action sheet, the event preview sheet and, since phase 4 part E1, the code
    /// field's paste button), each shot as `tour-control-…`.
    ///
    /// As the Android UI tour (`e2e/UiTourTest`), a destination it cannot reach is recorded with
    /// the reason, never as a failure: a `COPLANLY_TOUR <route>: skipped: …` line, in a text
    /// attachment and in the log, which CI keeps as `tour.txt` in the `ios-tour` artifact. Each
    /// visit starts from a tab at its first screen, so one that fails does not take the next with
    /// it. The tour fails only when the app does not launch or stops running during a visit (it is
    /// launched again and the tour goes on).
    func testTour() throws {
        let environment = ProcessInfo.processInfo.environment
        let email = try XCTUnwrap(environment["COPLANLY_UITEST_EMAIL"], "No seeded account was handed to the tour")
        let password = try XCTUnwrap(environment["COPLANLY_UITEST_PASSWORD"])
        tourShard = Self.shard(from: environment["COPLANLY_TOUR_SHARD"])

        app.launchEnvironment["COPLANLY_EMULATOR_HOST"] = "127.0.0.1"
        app.launch()
        // The two failure texts below are what `simulator.sh tour` looks for to tell a crash or a
        // failed launch from any other failure of the step (a timeout, the seed): keep them alike.
        guard app.wait(for: .runningForeground, timeout: 60) else {
            XCTFail("The app did not launch")
            return
        }

        touring = true
        activeTourBudget = tourBudget
        tourDeadline = Date().addingTimeInterval(tourBudget)
        // A walk that failed before its own sign-out leaves this simulator signed in, so the tour
        // may start on the tabs: it signs out through the app first, and a sign-out that fails is
        // only printed, the sign-in screen's visit then recording why it was not reached.
        do {
            try tourSignOutIfSignedIn()
        } catch {
            print("COPLANLY_TOUR_SIGN_OUT_FIRST failed: \(error)")
        }
        tourVisit("auth") {
            try self.require(self.app.staticTexts["Welcome back!"].waitForExistence(timeout: 120), "The sign-in screen did not show")
        }
        var signedIn = true
        do {
            try signIn(email: email, password: password)
        } catch {
            signedIn = false
            tourOutcomes.append(("sign-in", "skipped: \(error)"))
        }
        if signedIn { tourSignedIn() }
        let cause = signedIn ? "the tour has no way written to it" : "signing in failed, so nothing after the sign-in screen was reached"
        for route in tourRoutes where !tourOutcomes.contains(where: { $0.route == route }) {
            tourOutcomes.append((route, "skipped: " + (tourUnreachable[route] ?? cause)))
        }
        for control in tourControls where !tourOutcomes.contains(where: { $0.route == control }) {
            tourOutcomes.append((control, "skipped: " + cause))
        }
        touring = false
        tourFinish(routes: tourRoutes, controls: tourControls)
    }

    /// "1/2" as part 1 of 2; nil for anything else, which runs the whole tour.
    private static func shard(from text: String?) -> (index: Int, count: Int)? {
        guard let parts = text?.split(separator: "/"), parts.count == 2,
              let index = Int(parts[0]), let count = Int(parts[1]),
              count > 1, (1...count).contains(index) else { return nil }
        return (index, count)
    }

    /// The tour's summary, printed and attached: one `COPLANLY_TOUR <route>: <outcome>` line per
    /// destination and control visited, and how many of [routes] and [controls] were reached; then
    /// the one failure the tour has, the app having stopped running during a visit. `simulator.sh`
    /// reads both texts (`tour.txt`, `tour-crashed`), so `testTour` and `testTourAppearance` share
    /// them.
    private func tourFinish(routes: [String], controls: [String]) {
        let lines = tourOutcomes.map { "COPLANLY_TOUR \($0.route): \($0.outcome)" }
        print("COPLANLY_TOUR_SUMMARY_BEGIN")
        lines.forEach { print($0) }
        let reached = tourOutcomes.filter { routes.contains($0.route) && $0.outcome == "reached" }.count
        let shown = tourOutcomes.filter { controls.contains($0.route) && $0.outcome == "reached" }.count
        print(
            "COPLANLY_TOUR_SUMMARY reached \(reached) of \(routes.count) destinations, " +
                "\(shown) of \(controls.count) controls in use"
        )
        print("COPLANLY_TOUR_SUMMARY_END")
        let log = XCTAttachment(string: lines.joined(separator: "\n") + "\n")
        log.name = "tour-log"
        log.lifetime = .keepAlways
        add(log)
        if !tourCrashes.isEmpty {
            XCTFail("The app stopped running while the tour visited: " + tourCrashes.joined(separator: ", "))
        }
    }

    /// The seven screens of part D's boards, as `testTourAppearance` visits them: Home, Calendar,
    /// Chat, New event, Family, Expenses and Settings, by their routes.
    private let appearanceRoutes = [
        "home", "calendar", "conversations?draft={draft}", "add_event?date={date}&hour={hour}",
        "family", "expenses", "settings",
    ]

    /// The appearance tour's own budget: seven visits and a sign-in, inside a third of its CI step.
    private let appearanceBudget: TimeInterval = 7 * 60

    /// The board screens under one of the simulator's appearance settings (MON-24 phase 3, part D,
    /// Task 7): `simulator.sh tour-appearance` sets dark mode, Increase Contrast or the
    /// accessibility-extra-large text size with `simctl ui`, names it in `COPLANLY_TOUR_VARIANT`
    /// (`dark`, `contrast`, `large`) and runs this once per variant on a freshly seeded `--tour`
    /// family. It signs in as `testTour` does and shoots each of `appearanceRoutes` as
    /// `tour-<variant>-<route>`, with `testTour`'s visits, reset and rules: a screen it cannot reach is
    /// a `COPLANLY_TOUR <route>: skipped: …` line with the reason, never a failure, and the only
    /// failures are the app not launching or stopping during a visit (`tourFinish`).
    func testTourAppearance() throws {
        let environment = ProcessInfo.processInfo.environment
        let email = try XCTUnwrap(environment["COPLANLY_UITEST_EMAIL"], "No seeded account was handed to the tour")
        let password = try XCTUnwrap(environment["COPLANLY_UITEST_PASSWORD"])
        tourVariant = environment["COPLANLY_TOUR_VARIANT"] ?? "appearance"

        app.launchEnvironment["COPLANLY_EMULATOR_HOST"] = "127.0.0.1"
        app.launch()
        // `simulator.sh` tells a crash or a failed launch by testTour's two failure texts: the same here.
        guard app.wait(for: .runningForeground, timeout: 60) else {
            XCTFail("The app did not launch")
            return
        }

        touring = true
        activeTourBudget = appearanceBudget
        tourDeadline = Date().addingTimeInterval(appearanceBudget)
        // Each variant seeds an account of its own, so the app may still hold the previous one.
        do {
            try tourSignOutIfSignedIn()
        } catch {
            print("COPLANLY_TOUR_SIGN_OUT_FIRST failed: \(error)")
        }
        var signedIn = true
        do {
            try signIn(email: email, password: password)
        } catch {
            signedIn = false
            tourOutcomes.append(("sign-in", "skipped: \(error)"))
        }
        if signedIn {
            tourVisit("home") { try self.tourTab("Home") }
            tourVisit("calendar") { try self.tourTab("Calendar") }
            tourVisit("conversations?draft={draft}") {
                try self.tourTab("Chat")
                _ = self.element(labelledContaining: "Bob").waitForExistence(timeout: 30)
            }
            tourVisit("add_event?date={date}&hour={hour}") {
                try self.tourTab("Calendar")
                try self.tourOpenSheet(labelled: "Add event")
            }
            tourVisit("family") { try self.tourTab("Family") }
            tourVisit("expenses") { try self.tourFamilyRow("This month") }
            tourVisit("settings") { try self.tourSettings() }
        }
        let cause = signedIn ? "the appearance tour has no way written to it" : "signing in failed, so no board screen was reached"
        for route in appearanceRoutes where !tourOutcomes.contains(where: { $0.route == route }) {
            tourOutcomes.append((route, "skipped: " + cause))
        }
        touring = false
        tourFinish(routes: appearanceRoutes, controls: [])
    }

    /// Signs out through the app when the tour starts signed in: no sign-in screen within 15 s,
    /// but the tab bar. The steps are the walk's own (`signOutAndIn`): Settings from the Family
    /// tab's bar, "Sign out of app", and its confirmation.
    private func tourSignOutIfSignedIn() throws {
        if app.staticTexts["Welcome back!"].waitForExistence(timeout: 15) { return }
        let tabs = app.tabBars.firstMatch
        guard tabs.waitForExistence(timeout: 30) else { return }
        print("COPLANLY_TOUR_SIGN_OUT_FIRST the app started signed in; signing out")
        let family = tabs.buttons["Family"]
        try require(family.waitForExistence(timeout: timeout), "No Family tab to sign out from")
        family.tap()
        sleep(1)
        family.tap()
        let gear = app.navigationBars["Family"].buttons["Settings"]
        try require(gear.waitForExistence(timeout: timeout), "The Family tab's bar has no Settings")
        gear.tap()
        try require(app.navigationBars["Settings"].waitForExistence(timeout: timeout), "Settings did not open")
        let row = try element(labelled: "Sign out of app", in: app.buttons)
        let screenBottom = app.windows.firstMatch.frame.maxY - 120
        for _ in 0..<12 where row.frame.maxY > screenBottom {
            app.swipeUp()
        }
        sleep(1)
        press(row)
        try signOutConfirmation().tap()
        try require(
            app.staticTexts["Welcome back!"].waitForExistence(timeout: 120),
            "Signing out did not reach the sign-in screen"
        )
    }

    /// Every visit after signing in, each from a known place. The order keeps the ones that leave
    /// something open (the calendar's event preview, a link's screen) late.
    private func tourSignedIn() {
        // The four tabs.
        tourVisit("home") { try self.tourTab("Home") }
        tourVisit("family") { try self.tourTab("Family") }
        tourVisit("conversations?draft={draft}") {
            try self.tourTab("Chat")
            _ = self.element(labelledContaining: "Bob").waitForExistence(timeout: 30)
        }
        tourVisit("calendar") { try self.tourTab("Calendar") }

        // Home, whose first screen shows no navigation bar: the push is told by a bar appearing.
        tourVisit("change_requests?eventId={eventId}&section={section}") {
            try self.tourTab("Home")
            try self.tourOpen(containing: "See all", from: nil)
        }

        // The Family tab's rows.
        let familyRows: [(String, String)] = [
            ("expenses", "This month"),
            ("support", "Child support"),
            ("custody_setup?planQuestion={planQuestion}", "Custody schedule"),
            ("parenting_plan", "Parenting plan"),
            ("child_info", "Child information"),
            ("pets", "Pets"),
            ("contacts", "Contacts"),
            ("family_documents?family={family}", "Documents"),
            ("month_review", "Month in review"),
            ("export?thread={thread}&family={family}", "Export the record"),
            ("journal", "Private journal"),
            ("pairing?code={code}&enter={enter}&unpair={unpair}", "Linked: Bob"),
            ("coparent_profile", "What Bob has shared"),
            ("friends", "Family friends"),
            ("professionals", "Professionals"),
            ("stickers", "My stickers"),
        ]
        for (route, row) in familyRows {
            tourVisit(route) {
                try self.tourTab("Family")
                if route.hasPrefix("pairing") && !self.labelExists(containing: row) {
                    // The co-parent row reads "Linked: Bob" under its title once the link has reached the phone.
                    try self.tourOpen(containing: "Co-parent", from: "Family")
                } else {
                    try self.tourOpen(containing: row, from: "Family")
                }
            }
        }

        // One level further, from the Family tab.
        tourVisit("add_expense") {
            try self.tourFamilyRow("This month")
            try self.tourOpenSheet(labelled: "Add expense")
        }
        tourVisit("edit_expense/{expenseId}") {
            try self.tourFamilyRow("This month")
            try self.tourOpenSheet(containing: "Swimming course")
        }
        tourVisit("child_detail/{childInfoId}") {
            try self.tourFamilyRow("Child information")
            try self.tourOpen(containing: "Emma", from: nil)
        }
        tourVisit("edit_child_info/{childInfoId}") {
            try self.tourFamilyRow("Child information")
            try self.tourOpen(containing: "Emma", from: nil)
            try self.tourOpenSheet(labelled: "Edit")
        }
        // A pet opens its own screen first, then its editor, as a child does (24 November 2026).
        tourVisit("pet_detail/{petId}") {
            try self.tourFamilyRow("Pets")
            try self.tourOpen(containing: "Max", from: nil)
        }
        tourVisit("edit_pet/{petId}") {
            try self.tourFamilyRow("Pets")
            try self.tourOpen(containing: "Max", from: nil)
            try self.tourOpenSheet(labelled: "Edit")
        }
        tourVisit("journal_entry/{entryId}") {
            try self.tourFamilyRow("Private journal")
            try self.tourOpenSheet(labelled: "New entry")
        }

        // Settings and its screens.
        tourVisit("settings") { try self.tourSettings() }
        let settingsRows: [(String, String)] = [
            ("data_sources", dataSourcesTitle),
            ("calendar_feed", "Calendar feed for iPhone"),
            ("school_import", "Import from school"),
            ("my_profile", "My details"),
            ("sign_in_methods", signInMethodsTitle),
        ]
        for (route, row) in settingsRows {
            tourVisit(route) {
                try self.tourSettings()
                try self.tourOpen(containing: row, from: "Settings")
            }
        }
        tourVisit("school_connect?connectionId={connectionId}") {
            try self.tourSettings()
            try self.tourOpen(containing: "Import from school", from: "Settings")
            try self.tourOpen(containing: "Connect a school", from: nil)
        }

        // The calendar's forms, from its month card's "+" and from today's event.
        tourVisit("add_event?date={date}&hour={hour}") {
            try self.tourTab("Calendar")
            try self.tourOpenSheet(labelled: "Add event")
        }
        tourVisit("edit_event/{eventId}") { try self.tourOpenEvent() }
        tourVisit("request_change/{eventId}") {
            try self.tourOpenEvent()
            try self.tourOpenSheet(labelled: "Request change")
        }

        // The system's controls in use (part C), each from a known place; reset closes what it opens.
        tourControlVisits()

        // A link: a guest invitation's code, which the emulators do not hold.
        tourVisit("guest_accept?code={code}") {
            try self.tourTab("Family")
            self.app.open(URL(string: "coplanly://guest?code=ABC234")!)
            try self.require(self.element(labelledContaining: "Guest access").waitForExistence(timeout: 60), "The guest link did not open Guest access")
        }
    }

    /// The controls of `tourControls`, each left open for its screenshot (MON-24 phase 3, part C,
    /// Task 13; the paste button since iPhone phase 4, part E1). None saves or deletes anything: the
    /// switch is flipped back after its own shot, the option menu and the action sheet are left for
    /// `tourReset` to cancel, and the segment needs no undoing, since a tap on the Calendar tab opens
    /// Month again (CLAUDE.md design item 5).
    private func tourControlVisits() {
        // A switch: New event's "All day", a UISwitch, flipped on for a shot and back off.
        tourVisit("control-switch") {
            try self.tourTab("Calendar")
            try self.tourOpenSheet(labelled: "Add event")
            self.dismissKeyboard()
            let byLabel = self.app.switches.matching(NSPredicate(format: "label CONTAINS %@", "All day")).firstMatch
            let toggle = byLabel.waitForExistence(timeout: 8) ? byLabel : self.app.switches.firstMatch
            try self.require(toggle.waitForExistence(timeout: 8), "The event form shows no system switch")
            toggle.tap()
            sleep(1)
            self.shot("tour-control-switch-on")
            toggle.tap()
        }
        // A pop-up button: the calendar's view, its menu open, then Week chosen.
        tourVisit("control-view-menu") {
            try self.tourTab("Calendar")
            try self.require(self.viewButton.waitForExistence(timeout: 8), "The calendar has no view button")
            self.press(self.viewButton)
            let week = self.app.buttons.matching(NSPredicate(format: "label == %@", "Week")).firstMatch
            try self.require(week.waitForExistence(timeout: 8), "The view button opened no menu")
            self.shot("tour-control-view-menu-open")
            week.tap()
            try self.require(self.viewButton(reading: "Week").waitForExistence(timeout: 8), "Week did not select")
        }
        // The date sheet: "Jump to date", the inline calendar at half the screen.
        tourVisit("control-date-sheet") {
            try self.tourTab("Calendar")
            let title = self.calendarMonthButton
            try self.require(title.waitForExistence(timeout: 8), "The card's month title is not a button")
            self.press(title)
            let sheetBar = self.app.navigationBars.containing(NSPredicate(format: "label == %@", "Cancel")).firstMatch
            try self.require(sheetBar.waitForExistence(timeout: 8), "Jump to date did not open as a sheet with Cancel")
            try self.require(self.app.datePickers.firstMatch.exists, "The jump-to-date sheet holds no date picker")
        }
        // An option menu: New event's Repeat, the system's pop-up button, with its menu open; a tap
        // outside it after the shot closes it choosing nothing, as `tourReset` cannot (it has no Close).
        tourVisit("control-option-menu", afterShot: { self.tapOutside() }) {
            try self.tourTab("Calendar")
            try self.tourOpenSheet(labelled: "Add event")
            self.dismissKeyboard()
            let repeatButton = self.app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Repeat,")).firstMatch
            try self.require(repeatButton.waitForExistence(timeout: 8), "Repeat has no pop-up button named \"Repeat, …\"")
            let screenBottom = self.app.windows.firstMatch.frame.maxY - 160
            for _ in 0..<8 where repeatButton.frame.maxY > screenBottom {
                self.app.swipeUp()
            }
            sleep(1)
            self.press(repeatButton)
            let weekly = self.app.buttons.matching(NSPredicate(format: "label == %@", "Weekly")).firstMatch
            try self.require(weekly.waitForExistence(timeout: 8), "Repeat did not open a menu with \"Weekly\"")
        }
        // An action sheet: deleting today's seeded event asks; the tour never answers it.
        tourVisit("control-action-sheet") {
            try self.tourOpenEvent()
            let editBar = self.app.navigationBars["Edit event"]
            try self.require(editBar.waitForExistence(timeout: 8), "The event's form has no Edit event bar")
            let delete = editBar.buttons.matching(NSPredicate(format: "label == %@", "Delete event")).firstMatch
            try self.require(delete.waitForExistence(timeout: 8), "The event's form has no Delete event in its bar")
            self.press(delete)
            try self.require(self.app.sheets.firstMatch.waitForExistence(timeout: 8), "Delete did not ask in the system's action sheet")
        }
        // The event preview: the system's sheet at half the screen, the calendar above it.
        tourVisit("control-preview-sheet") {
            try self.tourTab("Calendar")
            let edit = self.app.buttons.matching(NSPredicate(format: "label == %@", "Edit")).firstMatch
            try self.openPreview(of: self.tourEventTitle, edit: edit)
        }
        // The system's paste button beside the code field (iPhone phase 4, part E1): Family, the
        // co-parent's row, "Add another co-parent", then "Enter a code" opens the field; nothing is
        // pasted. Opening the row mints a code on the emulators, as the share sheet's walk does.
        tourVisit("control-paste") {
            try self.tourTab("Family")
            if self.labelExists(containing: "Linked: ") {
                try self.tourOpen(containing: "Linked: ", from: "Family")
            } else {
                try self.tourOpen(containing: "Co-parent", from: "Family")
            }
            self.press(try self.element(containing: "Add another co-parent"))
            let enter = self.app.buttons["Enter a code"]
            try self.require(enter.waitForExistence(timeout: 60), "Add another co-parent offers no Enter a code")
            // The invitation's QR above it can push the button below the fold.
            let screenBottom = self.app.windows.firstMatch.frame.maxY - 120
            for _ in 0..<6 where enter.frame.maxY > screenBottom {
                self.app.swipeUp()
            }
            sleep(1)
            self.press(enter)
            self.dismissKeyboard()
            let paste = self.app.descendants(matching: .any)
                .matching(NSPredicate(format: "label == %@", "Paste")).firstMatch
            try self.require(paste.waitForExistence(timeout: 8), "The code field shows no system paste button named \"Paste\"")
        }
    }

    /// Runs one visit to [route]: back to a known place, [reach], a screenshot, then [afterShot]
    /// (what a visit leaves open that `tourReset` cannot close). A visit that throws, or during which
    /// XCUITest recorded an issue, is skipped with the reason, a screenshot and the element tree.
    private func tourVisit(_ route: String, afterShot: (() -> Void)? = nil, _ reach: () throws -> Void) {
        if let shard = tourShard {
            let mine = tourVisitsDealt % shard.count == shard.index - 1
            tourVisitsDealt += 1
            if !mine {
                tourOutcomes.append((route, "in another part of the tour"))
                return
            }
        }
        if Date() > tourDeadline {
            let outcome = "skipped: the tour's \(Int(activeTourBudget / 60)) minutes ran out before this visit"
            print("COPLANLY_TOUR_VISIT \(route): \(outcome)")
            tourOutcomes.append((route, outcome))
            return
        }
        tourIssue = nil
        var outcome = "reached"
        XCTContext.runActivity(named: "tour \(route)") { _ in
            do {
                if route != "auth" { tourReset() }
                try reach()
                sleep(2)
                if let issue = tourIssue {
                    outcome = "skipped: \(issue)"
                } else {
                    shot("tour-" + (tourVariant.map { "\($0)-" } ?? "") + tourShotName(route))
                }
            } catch {
                outcome = "skipped: \(error)"
            }
            if outcome.hasPrefix("skipped") { tourDiagnose(route) }
            afterShot?()
        }
        if app.state != .runningForeground {
            tourCrashes.append(route)
            outcome = "skipped: the app stopped running (state \(app.state.rawValue)) during the visit"
            app.launch()
            _ = app.tabBars.firstMatch.waitForExistence(timeout: 120)
        }
        // As it goes, for a tour that never reaches its summary (the step's time limit).
        print("COPLANLY_TOUR_VISIT \(route): \(outcome)")
        tourOutcomes.append((route, outcome))
    }

    /// What was on screen when [route] was skipped: a screenshot and the element tree, so a skip can
    /// be told apart from a screen that is not there without running the tour again.
    private func tourDiagnose(_ route: String) {
        guard app.state == .runningForeground else { return }
        let name = "skip-" + tourShotName(route)
        let screen = XCTAttachment(screenshot: app.screenshot())
        screen.name = name
        screen.lifetime = .keepAlways
        add(screen)
        let tree = XCTAttachment(string: app.debugDescription)
        tree.name = name + "-tree"
        tree.lifetime = .keepAlways
        add(tree)
    }

    /// A tap at the top of the screen, outside a menu or an action sheet, which closes it choosing nothing.
    private func tapOutside() {
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.06)).tap()
        sleep(1)
    }

    /// [route] up to its first `/` or `?`: `edit_event/{eventId}` is shot as `tour-edit_event`.
    private func tourShotName(_ route: String) -> String {
        String(route.prefix { $0 != "/" && $0 != "?" })
    }

    /// Closes what an earlier visit left open — a question, a sheet, the event preview — so the
    /// next starts from a tab.
    private func tourReset() {
        var tappedOutside = false
        for _ in 0..<4 {
            // A question in the system's action sheet: its Cancel, never its action.
            let sheetCancel = app.sheets.firstMatch.buttons["Cancel"]
            if sheetCancel.exists {
                sheetCancel.tap()
                sleep(1)
            }
            // A picker's sheet (Jump to date) closes with its bar's Cancel, which saves nothing; only a
            // picker's, never another bar's Cancel, which may be a form's own.
            let pickerCancel = app.navigationBars.buttons.matching(NSPredicate(format: "label == %@", "Cancel")).firstMatch
            let pickerShown = app.datePickers.firstMatch.exists || app.pickers.firstMatch.exists
            if pickerShown && pickerCancel.exists && pickerCancel.isHittable {
                pickerCancel.tap()
                sleep(1)
            }
            if app.staticTexts["Discard changes?"].exists { pressIfThere("Discard") }
            let closes = app.navigationBars.buttons.matching(NSPredicate(format: "label == %@", "Close"))
            // The sheet on top is the one whose cross can be touched.
            let top = (0..<closes.count).reversed().map { closes.element(boundBy: $0) }.first { $0.isHittable }
            guard let top else {
                // A sheet is up but its cross does not read as touchable: something over it (a menu)
                // takes the touches. A tap outside closes that, and the next round finds the cross.
                if closes.count > 0 && !tappedOutside {
                    tappedOutside = true
                    tapOutside()
                    continue
                }
                // Still not "hittable" after that: XCUITest's hit test can misread a native sheet
                // (run 10 left New event up under every later visit this way), so the topmost cross
                // is tapped where it is drawn; a menu still over it takes that tap and closes, and the
                // next round taps the cross again.
                if closes.count > 0 {
                    press(closes.element(boundBy: closes.count - 1))
                    sleep(1)
                    continue
                }
                break
            }
            top.tap()
            sleep(1)
        }
        if app.staticTexts["Discard changes?"].exists { pressIfThere("Discard") }
        // A sheet still over the tab bar after its crosses (run 11: every later tab tap computed the
        // hit point {-1, -1}, so the Calendar's and the Family tab's visits were skipped): swiped down
        // from its bar, as a parent would close it, answering "Discard" if it asks.
        let calendarTab = app.tabBars.firstMatch.buttons["Calendar"]
        for _ in 0..<3 where calendarTab.exists && !calendarTab.isHittable {
            let bar = app.navigationBars.firstMatch
            guard bar.exists else { break }
            swipeSheetDown(from: bar)
            sleep(1)
            if app.staticTexts["Discard changes?"].exists { pressIfThere("Discard") }
        }
        // The calendar's event preview is a sheet of the calendar's own: a tap on its scrim, above
        // it, closes it.
        if app.buttons.matching(NSPredicate(format: "label == %@", "Edit")).firstMatch.exists &&
            app.tabBars.firstMatch.buttons["Calendar"].isSelected {
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.25)).tap()
            sleep(1)
        }
    }

    /// Shows the tab labelled [name] at its first screen.
    private func tourTab(_ name: String) throws {
        let tab = app.tabBars.firstMatch.buttons[name]
        try require(tab.waitForExistence(timeout: timeout), "No \(name) tab")
        tab.tap()
        sleep(1)
        // A second tap on the selected tab pops it to its first screen.
        tab.tap()
        sleep(1)
        try require(tab.isSelected, "The \(name) tab did not select")
        if name == "Family" {
            try require(app.navigationBars[name].waitForExistence(timeout: timeout), "\(name)'s first screen is missing")
        }
        // Home and the Calendar lead with the wordmark header, under no navigation bar.
        if name == "Home" || name == "Calendar" { try requireHeaderRoot(name) }
    }

    /// The Family tab's row reading [row], opened.
    private func tourFamilyRow(_ row: String) throws {
        try tourTab("Family")
        try tourOpen(containing: row, from: "Family")
    }

    /// Settings, opened from the Family tab's bar.
    private func tourSettings() throws {
        try tourTab("Family")
        let gear = app.navigationBars["Family"].buttons["Settings"]
        try require(gear.waitForExistence(timeout: timeout), "The Family tab's bar has no Settings")
        gear.tap()
        try require(app.navigationBars["Settings"].waitForExistence(timeout: timeout), "Settings did not open")
    }

    /// Scrolls the element reading [text] onto the screen and taps it; it is reached once the bar
    /// titled [from] has gone (a push), or, with no [from], once the screen's bar title changed (a
    /// bar appearing counts, over a first screen that shows none).
    private func tourOpen(containing text: String, from: String?) throws {
        let target = try tourScrolledTo(text)
        // Home's and the Calendar's first screens show no bar: a push is then a bar appearing.
        let before = tourBarTitle()
        press(target)
        let gone: () -> Bool = {
            if let from { return self.app.navigationBars[from].waitForNonExistence(timeout: 4) }
            for _ in 0..<8 {
                if self.tourBarTitle() != before { return true }
                usleep(500_000)
            }
            return false
        }
        if !gone() {
            // A tap while the list still glides after a swipe only stops it: once more.
            sleep(1)
            if target.exists { press(target) }
            try require(gone(), "Tapping \"\(text)\" opened no other screen")
        }
    }

    /// The identifier of the navigation bar on screen, or nil while there is none.
    private func tourBarTitle() -> String? {
        let bar = app.navigationBars.firstMatch
        return bar.exists ? bar.identifier : nil
    }

    /// Taps the button labelled [label] (or else whatever reads it) and requires a form sheet: a
    /// bar with a Close button over the screen.
    private func tourOpenSheet(labelled label: String) throws {
        try tourOpenSheet(target: try tourButton(label), what: label)
    }

    /// The button labelled [label], or else whatever reads it, with shorter waits than the walk's
    /// `element(labelled:in:)`: a miss in the tour costs seconds, not most of a minute.
    private func tourButton(_ label: String) throws -> XCUIElement {
        let exact = app.buttons.matching(NSPredicate(format: "label == %@", label)).firstMatch
        if exact.waitForExistence(timeout: 8) { return exact }
        let loose = element(labelledContaining: label)
        try require(loose.waitForExistence(timeout: 2), "Nothing on screen reads \"\(label)\"")
        return loose
    }

    /// Taps what reads [text], scrolled onto the screen, and requires a form sheet.
    private func tourOpenSheet(containing text: String) throws {
        try tourOpenSheet(target: try tourScrolledTo(text), what: text)
    }

    /// Taps [target] and requires a new sheet: one more bar with a Close button, or a bar whose
    /// title was not on screen before (a form over a form may hide the bar under it).
    private func tourOpenSheet(target: XCUIElement, what: String) throws {
        let closes = app.navigationBars.buttons.matching(NSPredicate(format: "label == %@", "Close"))
        let closesBefore = closes.count
        let titlesBefore = Set(app.navigationBars.allElementsBoundByIndex.map { $0.identifier })
        press(target)
        for _ in 0..<20 {
            if closes.count > closesBefore { return }
            let titles = Set(app.navigationBars.allElementsBoundByIndex.map { $0.identifier })
            if closes.count > 0 && !titles.isSubset(of: titlesBefore) { return }
            usleep(500_000)
        }
        throw StepFailure(description: "Tapping \"\(what)\" opened no form sheet")
    }

    /// The element reading [text], scrolled until all of it is on the screen: below the navigation
    /// bar (a list left scrolled down by an earlier visit holds rows above it, which a tap would miss)
    /// and above the tab bar. It looks further down first, then back up.
    private func tourScrolledTo(_ text: String) throws -> XCUIElement {
        let match = element(labelledContaining: text)
        let window = app.windows.firstMatch.frame
        let bottom = window.maxY - 120
        for attempt in 0..<12 {
            let bar = app.navigationBars.firstMatch
            let top = bar.exists ? max(bar.frame.maxY, window.minY) : window.minY + 60
            // The screen's first frame may take a moment; after a swipe the list is already there.
            if match.waitForExistence(timeout: attempt == 0 ? 8 : 1) && !match.frame.isEmpty {
                let frame = match.frame
                if frame.minY >= top && frame.maxY <= bottom {
                    sleep(1)
                    return match
                }
                if frame.minY < top {
                    app.swipeDown()
                    continue
                }
                app.swipeUp()
                continue
            }
            // Not in the tree: further down for six swipes, then back up.
            if attempt < 6 { app.swipeUp() } else { app.swipeDown() }
        }
        try require(match.exists, "Nothing on screen reads \"\(text)\"")
        return match
    }

    /// The edit form of today's seeded event: the month's pill opens Day view, the event's block
    /// there its preview, and the preview's Edit the form, as a sheet.
    private func tourOpenEvent() throws {
        try tourTab("Calendar")
        let edit = app.buttons.matching(NSPredicate(format: "label == %@", "Edit")).firstMatch
        for _ in 0..<3 where !edit.exists {
            guard let event = tourTopmost(containing: tourEventTitle) else {
                throw StepFailure(description: "Nothing on the calendar reads \"\(tourEventTitle)\"")
            }
            press(event)
            _ = edit.waitForExistence(timeout: 5)
        }
        try require(edit.exists, "The event's preview did not open")
        try tourOpenSheet(target: edit, what: "Edit")
    }

    /// Of what reads [text], the element drawn highest on the screen (the grid's, not the today
    /// card's under it), or nil when nothing does.
    private func tourTopmost(containing text: String) -> XCUIElement? {
        let matches = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", text))
        guard matches.firstMatch.waitForExistence(timeout: 15) else { return nil }
        var best: XCUIElement?
        for index in 0..<min(matches.count, 12) {
            let candidate = matches.element(boundBy: index)
            let frame = candidate.frame
            guard !frame.isEmpty, frame.minY > 0 else { continue }
            if best == nil || frame.minY < best!.frame.minY { best = candidate }
        }
        return best
    }
}
