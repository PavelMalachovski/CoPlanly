import XCTest

/// The native shell on a simulator, against the Firebase emulators (MON-24 phase 3, Task 8).
///
/// It signs in by e-mail as the first parent of the family `tools/ios/seed-shell-family.js` seeded,
/// walks the path part A converts — Home, Family, Expenses, the Add expense sheet, Settings — and
/// saves a screenshot of each as an attachment, which CI exports as the `ios-shell` artifact. On the
/// way it does what the shell's review is about: a back swipe, a back swipe let go of halfway, a
/// second tap on the selected tab popping two screens, and a sheet holding an edit swiped down,
/// which must ask "Discard changes?" and stay open on "Keep editing".
///
/// The texts it looks for are the shared strings in English, the simulator's language.
final class ShellUITests: XCTestCase {
    private let timeout: TimeInterval = 60

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    /// On any failure, what the app showed: a screenshot attachment and its accessibility hierarchy
    /// in the log, so a run read only through its log still says where the walk stopped.
    override func record(_ issue: XCTIssue) {
        let app = XCUIApplication()
        shot(app, "zz-failure")
        print("COPLANLY_UI_HIERARCHY_BEGIN\n\(app.debugDescription)\nCOPLANLY_UI_HIERARCHY_END")
        super.record(issue)
    }

    func testShellWalk() throws {
        let environment = ProcessInfo.processInfo.environment
        let email = try XCTUnwrap(environment["COPLANLY_UITEST_EMAIL"], "No seeded account was handed to the test")
        let password = try XCTUnwrap(environment["COPLANLY_UITEST_PASSWORD"])

        let app = XCUIApplication()
        // The emulators, never a real project: the build has no GoogleService-Info.plist either.
        app.launchEnvironment["COPLANLY_EMULATOR_HOST"] = "127.0.0.1"
        app.launch()

        signIn(app, email: email, password: password)

        // Home: the tab bar and the large title.
        let tabs = app.tabBars.firstMatch
        XCTAssertTrue(tabs.buttons["Home"].waitForExistence(timeout: timeout), "No tab bar after signing in")
        XCTAssertTrue(app.navigationBars["Home"].waitForExistence(timeout: timeout))
        shot(app, "01-home")

        // The two tabs that keep their Compose headers in part A still open.
        tabs.buttons["Calendar"].tap()
        tabs.buttons["Chat"].tap()

        // Family, then Expenses from the money row.
        tabs.buttons["Family"].tap()
        XCTAssertTrue(app.navigationBars["Family"].waitForExistence(timeout: timeout))
        shot(app, "02-family")
        openExpenses(app)
        shot(app, "03-expenses")

        // A back swipe from the edge returns to Family, and the money row opens Expenses again.
        swipeBack(app, to: 0.85)
        XCTAssertTrue(app.navigationBars["Family"].waitForExistence(timeout: timeout), "The back swipe did not pop")
        openExpenses(app)

        // A swipe let go of early leaves Expenses on screen, and the shell still works from there.
        swipeBack(app, to: 0.12)
        sleep(1)
        XCTAssertTrue(app.navigationBars["Expenses"].exists, "A cancelled back swipe popped the screen")

        // The Add expense sheet, an edit, and a swipe down that must ask first.
        press(element(app, labelled: "Add expense", in: app.buttons))
        let sheetBar = app.navigationBars["Add expense"]
        XCTAssertTrue(sheetBar.waitForExistence(timeout: timeout), "The form did not open as a sheet")
        shot(app, "04-add-expense-sheet")
        type("Shell check", into: field(app, labelled: "Title"), in: app)
        dismissKeyboard(app)
        swipeSheetDown(app, from: sheetBar)
        let question = app.staticTexts["Discard changes?"]
        XCTAssertTrue(question.waitForExistence(timeout: timeout), "A swipe over edits did not ask first")
        shot(app, "05-discard-question")
        press(element(app, labelled: "Keep editing", in: app.buttons))
        XCTAssertTrue(sheetBar.waitForExistence(timeout: timeout))
        XCTAssertFalse(question.exists, "Keep editing left the question up")
        XCTAssertTrue(sheetBar.exists, "Keep editing closed the sheet")

        // The close button asks too; Discard leaves the form and the sheet.
        sheetBar.buttons.firstMatch.tap()
        XCTAssertTrue(question.waitForExistence(timeout: timeout), "Closing over edits did not ask")
        press(element(app, labelled: "Discard", in: app.buttons))
        XCTAssertTrue(app.navigationBars["Expenses"].waitForExistence(timeout: timeout), "Discard did not close the sheet")

        // The system back button returns to Family; its gear opens Settings, with a large title.
        app.navigationBars["Expenses"].buttons.element(boundBy: 0).tap()
        XCTAssertTrue(app.navigationBars["Family"].waitForExistence(timeout: timeout))
        openExpenses(app)
        app.navigationBars["Expenses"].buttons.element(boundBy: 0).tap()
        XCTAssertTrue(app.navigationBars["Family"].waitForExistence(timeout: timeout))
        app.navigationBars["Family"].buttons["Settings"].tap()
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: timeout))
        shot(app, "06-settings")

        // A screen that still draws its own Compose bar is pushed with the system bar hidden, and a
        // second tap on the selected tab pops both it and Settings, and the stack still works.
        let dataSources = element(app, containing: "Data sources and licences")
        let screenBottom = app.windows.firstMatch.frame.maxY - 120
        for _ in 0..<6 where dataSources.frame.maxY > screenBottom {
            app.swipeUp()
        }
        press(dataSources)
        XCTAssertTrue(app.staticTexts["Data sources and licences"].waitForExistence(timeout: timeout))
        sleep(1)
        let systemBar = app.navigationBars.firstMatch
        XCTAssertFalse(systemBar.exists && systemBar.isHittable, "Two bars: the system bar over a Compose bar")
        shot(app, "07-compose-bar-screen")
        tabs.buttons["Family"].tap()
        XCTAssertTrue(app.navigationBars["Family"].waitForExistence(timeout: timeout), "The tab tap did not pop")
        openExpenses(app)
        app.navigationBars["Expenses"].buttons.element(boundBy: 0).tap()
        XCTAssertTrue(app.navigationBars["Family"].waitForExistence(timeout: timeout))
        shot(app, "08-family-again")
    }

    private func signIn(_ app: XCUIApplication, email: String, password: String) {
        // The sign-in screen's own text, before its fields: Compose draws the screen first.
        _ = app.staticTexts["Welcome back!"].waitForExistence(timeout: 120)
        shot(app, "00-sign-in")
        print("COPLANLY_UI_HIERARCHY_BEGIN\n\(app.debugDescription)\nCOPLANLY_UI_HIERARCHY_END")
        type(email, into: field(app, labelled: "Email address"), in: app)
        type(password, into: field(app, labelled: "Password"), in: app)
        dismissKeyboard(app)
        press(element(app, labelled: "Sign In", in: app.buttons))
    }

    /// A text field by its label. Compose exposes a text field as a text view whose label is the
    /// static text inside it; a text field proper is looked for too.
    private func field(_ app: XCUIApplication, labelled label: String) -> XCUIElement {
        let byLabel = NSPredicate(format: "label == %@", label)
        let inside = app.textViews.containing(byLabel).firstMatch
        if inside.waitForExistence(timeout: timeout) { return inside }
        let field = app.textFields.matching(byLabel).firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 5), "No field labelled \"\(label)\"")
        return field
    }

    /// Types [text] into [field] through the keyboard, once a tap has focused it.
    private func type(_ text: String, into field: XCUIElement, in app: XCUIApplication) {
        press(field)
        _ = app.keyboards.firstMatch.waitForExistence(timeout: 5)
        app.typeText(text)
    }

    /// Closes the keyboard with its own key, so what it covered can be tapped.
    private func dismissKeyboard(_ app: XCUIApplication) {
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

    private func openExpenses(_ app: XCUIApplication) {
        press(element(app, containing: "This month"))
        XCTAssertTrue(app.navigationBars["Expenses"].waitForExistence(timeout: timeout), "Expenses did not open")
    }

    /// A drag from the left edge to [fraction] of the width: past half pops, a short one is let go of.
    private func swipeBack(_ app: XCUIApplication, to fraction: CGFloat) {
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.0, dy: 0.5))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: fraction, dy: 0.5))
        start.press(forDuration: 0.05, thenDragTo: end)
    }

    private func swipeSheetDown(_ app: XCUIApplication, from bar: XCUIElement) {
        let start = bar.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.2))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.95))
        start.press(forDuration: 0.05, thenDragTo: end)
    }

    private func element(_ app: XCUIApplication, labelled label: String, in query: XCUIElementQuery) -> XCUIElement {
        let exact = query[label]
        if exact.waitForExistence(timeout: 10) { return exact }
        return element(app, containing: label)
    }

    private func element(_ app: XCUIApplication, containing text: String) -> XCUIElement {
        let match = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", text))
            .firstMatch
        XCTAssertTrue(match.waitForExistence(timeout: timeout), "Nothing on screen reads \"\(text)\"")
        return match
    }

    private func shot(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
