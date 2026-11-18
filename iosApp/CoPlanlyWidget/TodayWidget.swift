import Foundation
import SwiftUI
import WidgetKit

/// The "Today" widget on the iPhone's home screen (iPhone phase 4 part D, design §7.3): the
/// Calendar's today card, as Android's Glance widget draws it (CLAUDE.md design item 18).
///
/// The extension words and computes nothing. The app writes seven days of finished lines and
/// colours to `<App Group container>/widget/today.json` (`WidgetSnapshotWriter.kt`, the shape of
/// `WidgetSnapshot.kt`), and this only picks the day it is and draws it. Its one sentence of its
/// own is the English line for a phone where the app has never written a snapshot.
@main
struct TodayWidget: Widget {
    /// The widget's kind; `WidgetCenter.reloadAllTimelines()` in the app reaches it.
    let kind: String = "TodayWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: TodayProvider()) { entry in
            TodayWidgetView(entry: entry)
        }
        .configurationDisplayName("Today")
        .description("Whose day it is, the next handover and today's events.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

// MARK: - The snapshot, as the app writes it

/// `WidgetColour`: a colour as "#RRGGBB" in each appearance.
struct WidgetColour: Decodable, Equatable {
    let light: String
    let dark: String
}

/// `WidgetSnapshotLine`: one line and its colour, or none for the neutral text colour.
struct WidgetSnapshotLine: Decodable, Equatable {
    let text: String
    let colour: WidgetColour?
}

/// `WidgetSnapshotEvent`: one event row and its owner's mark colour.
struct WidgetSnapshotEvent: Decodable, Equatable {
    let time: String
    let title: String
    let mark: WidgetColour?
}

/// `WidgetDayLines`: one day at one size. Kotlin leaves a null out of the JSON, so every optional
/// here may be missing as well as null.
struct WidgetDayLines: Decodable, Equatable {
    let title: String
    let custody: WidgetSnapshotLine?
    let windows: [WidgetSnapshotLine]
    let handover: WidgetSnapshotLine?
    let events: [WidgetSnapshotEvent]
    let footer: String?
}

/// `WidgetDay`: one day, "yyyy-MM-dd", at each size the widget is drawn at.
struct WidgetDay: Decodable, Equatable {
    let date: String
    let compact: WidgetDayLines
    let compactLarge: WidgetDayLines
    let tall: WidgetDayLines
}

/// `WidgetSnapshot`, read for what the widget draws; the keys it does not need are ignored.
struct WidgetSnapshot: Decodable, Equatable {
    /// Kotlin's default is 1; a missing key reads as that, as `StoredJson` would read it.
    let version: Int?
    let days: [WidgetDay]
    let staleLine: String
}

// MARK: - The timeline

/// What one entry draws.
enum TodayContent: Equatable {
    /// A day of the snapshot.
    case day(WidgetDay)
    /// Every day of the snapshot has passed: the snapshot's own "open the app" line.
    case stale(String)
    /// No snapshot, or one this extension cannot read.
    case signedOut
}

/// One timeline entry.
struct TodayEntry: TimelineEntry {
    let date: Date
    let content: TodayContent
}

/// Reads the snapshot and makes the timeline from it.
struct TodayProvider: TimelineProvider {
    func placeholder(in context: Context) -> TodayEntry {
        TodayEntry(date: Date(), content: .day(TodaySample.day))
    }

    func getSnapshot(in context: Context, completion: @escaping (TodayEntry) -> Void) {
        completion(TodayEntry(date: Date(), content: .day(TodaySample.day)))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<TodayEntry>) -> Void) {
        let entries = TodayTimeline.entries(
            snapshot: TodaySnapshotFile.read(),
            now: Date(),
            calendar: Calendar.current
        )
        completion(Timeline(entries: entries, policy: .atEnd))
    }
}

/// The timeline's rule: one entry per day at that day's local midnight (today's at now), then the
/// stale line from the day after the last; a missing or unreadable snapshot is one signed-out entry.
enum TodayTimeline {
    /// The extension's only own text: no snapshot has been written on this phone yet.
    static let signedOutLine = "Sign in to CoPlanly to see today."

    /// The entries for [snapshot] at [now], days placed in [calendar]'s time zone.
    static func entries(snapshot: WidgetSnapshot?, now: Date, calendar: Calendar) -> [TodayEntry] {
        guard let snapshot, (snapshot.version ?? 1) == 1 else {
            return [TodayEntry(date: now, content: .signedOut)]
        }
        let today = calendar.startOfDay(for: now)
        var entries: [TodayEntry] = []
        var lastDay: Date?
        for day in snapshot.days {
            guard let start = startOfDay(day.date, calendar: calendar) else { continue }
            if let last = lastDay, last >= start {
                // Kept in order; a day out of order or repeated is not drawn.
                continue
            }
            lastDay = start
            if start < today { continue }
            entries.append(TodayEntry(date: start == today ? now : start, content: .day(day)))
        }
        guard let lastDay else {
            return [TodayEntry(date: now, content: .signedOut)]
        }
        let afterLast = calendar.date(byAdding: .day, value: 1, to: lastDay) ?? lastDay
        entries.append(TodayEntry(date: max(afterLast, now), content: .stale(snapshot.staleLine)))
        return entries
    }

    /// The local midnight of an ISO day ("2026-11-18"), read in the Gregorian calendar whatever the
    /// phone's calendar is (a Buddhist or Japanese year would shift it), in [calendar]'s zone.
    static func startOfDay(_ iso: String, calendar: Calendar) -> Date? {
        let parts = iso.split(separator: "-")
        guard parts.count == 3,
              let year = Int(parts[0]), let month = Int(parts[1]), let day = Int(parts[2]) else {
            return nil
        }
        var gregorian = Calendar(identifier: .gregorian)
        gregorian.timeZone = calendar.timeZone
        // Noon, which exists on every day in every zone; then back to the day's first moment.
        let components = DateComponents(year: year, month: month, day: day, hour: 12)
        guard let noon = gregorian.date(from: components) else { return nil }
        return gregorian.startOfDay(for: noon)
    }
}

/// The snapshot file in the App Group.
enum TodaySnapshotFile {
    private static let group = "group.app.coplanly"

    /// The snapshot, or nil when there is none or it cannot be read.
    static func read() -> WidgetSnapshot? {
        guard let container = FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: group) else {
            return nil
        }
        let file = container
            .appendingPathComponent("widget", isDirectory: true)
            .appendingPathComponent("today.json", isDirectory: false)
        guard let data = try? Data(contentsOf: file) else { return nil }
        return try? JSONDecoder().decode(WidgetSnapshot.self, from: data)
    }
}

/// What the widget gallery and a placeholder show: a made-up day, in English.
enum TodaySample {
    static let day: WidgetDay = {
        let custody = WidgetSnapshotLine(
            text: "Today with Alex",
            colour: WidgetColour(light: "#A0124F", dark: "#FF99BB")
        )
        let window = WidgetSnapshotLine(
            text: "15:00–19:00 with Bob",
            colour: WidgetColour(light: "#1550C0", dark: "#9EC2FF")
        )
        let handover = WidgetSnapshotLine(text: "Handover to you in 2 days", colour: nil)
        let title = "Today · Thu, 19 Nov"
        let events = [
            WidgetSnapshotEvent(
                time: "09:00",
                title: "Dentist – Emma",
                mark: WidgetColour(light: "#E91E63", dark: "#FF4081")
            ),
            WidgetSnapshotEvent(
                time: "14:00",
                title: "Swimming",
                mark: WidgetColour(light: "#2962FF", dark: "#82B1FF")
            )
        ]
        let compact = WidgetDayLines(
            title: title, custody: custody, windows: [window], handover: handover,
            events: [], footer: "2 events today"
        )
        let compactLarge = WidgetDayLines(
            title: title, custody: custody, windows: [], handover: handover,
            events: [], footer: nil
        )
        let tall = WidgetDayLines(
            title: title, custody: custody, windows: [window], handover: handover,
            events: events, footer: nil
        )
        return WidgetDay(date: "2026-11-19", compact: compact, compactLarge: compactLarge, tall: tall)
    }()
}
