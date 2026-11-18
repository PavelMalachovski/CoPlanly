import SwiftUI
import UIKit
import WidgetKit

/// Draws one [TodayEntry] (design §7.3): the small size the snapshot's `compact` lines, or
/// `compactLarge` at the accessibility text sizes (Android's `compactAtLargeText` from font scale
/// 1.3), the medium size its `tall` lines with the event rows. A tap opens the Calendar on today.
struct TodayWidgetView: View {
    let entry: TodayEntry

    @Environment(\.widgetFamily) private var family
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .containerBackground(for: .widget) {
                Color(UIColor.systemBackground)
            }
            .widgetURL(URL(string: "coplanly://calendar"))
    }

    @ViewBuilder
    private var content: some View {
        switch entry.content {
        case .day(let day):
            TodayDayView(lines: lines(of: day), showsEvents: family == .systemMedium)
        case .stale(let line):
            TodayMessageView(text: line)
        case .signedOut:
            TodayMessageView(text: TodayTimeline.signedOutLine)
        }
    }

    private func lines(of day: WidgetDay) -> WidgetDayLines {
        if family == .systemMedium {
            return day.tall
        }
        return dynamicTypeSize.isAccessibilitySize ? day.compactLarge : day.compact
    }
}

/// One day's lines. Small: one column — the title, whose day it is, the contact windows, the
/// handover and the footer. Medium: two columns, top-aligned — those lines on the left (the footer
/// apart), the event rows and the footer on the right — because `tall` holds up to four events,
/// which one column of a medium widget cannot fit under the rest. Every line is limited, so a column
/// never runs past the widget.
private struct TodayDayView: View {
    let lines: WidgetDayLines
    let showsEvents: Bool

    var body: some View {
        if showsEvents {
            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    TodaySummaryLines(lines: lines)
                    Spacer(minLength: 0)
                }
                .frame(maxWidth: .infinity, alignment: .topLeading)
                VStack(alignment: .leading, spacing: 3) {
                    ForEach(Array(lines.events.enumerated()), id: \.offset) { _, event in
                        TodayEventRow(event: event)
                    }
                    TodayFooter(text: lines.footer)
                    Spacer(minLength: 0)
                }
                .frame(maxWidth: .infinity, alignment: .topLeading)
            }
        } else {
            VStack(alignment: .leading, spacing: 2) {
                TodaySummaryLines(lines: lines)
                TodayFooter(text: lines.footer)
                Spacer(minLength: 0)
            }
        }
    }
}

/// The title, whose day it is, the contact windows and the handover.
private struct TodaySummaryLines: View {
    let lines: WidgetDayLines

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        Text(lines.title)
            .font(.headline)
            .foregroundStyle(Color.primary)
            .lineLimit(1)
            .minimumScaleFactor(0.8)
        if let custody = lines.custody {
            Text(custody.text)
                .font(.subheadline)
                .foregroundStyle(TodayColours.colour(custody.colour, in: colorScheme, else: .primary))
                .lineLimit(2)
        }
        ForEach(Array(lines.windows.enumerated()), id: \.offset) { _, window in
            Text(window.text)
                .font(.caption)
                .foregroundStyle(TodayColours.colour(window.colour, in: colorScheme, else: .secondary))
                .lineLimit(1)
        }
        if let handover = lines.handover {
            Text(handover.text)
                .font(.caption)
                .foregroundStyle(TodayColours.colour(handover.colour, in: colorScheme, else: .secondary))
                .lineLimit(2)
        }
    }
}

/// What is not drawn, said in words ("+1 more"), when the snapshot has a footer.
private struct TodayFooter: View {
    let text: String?

    var body: some View {
        if let text {
            Text(text)
                .font(.caption)
                .foregroundStyle(Color.secondary)
                .lineLimit(2)
        }
    }
}

/// One event: a 3 pt rounded mark in the owner's colour, the time and the title on one line.
private struct TodayEventRow: View {
    let event: WidgetSnapshotEvent

    @Environment(\.colorScheme) private var colorScheme
    @ScaledMetric(relativeTo: .caption) private var markHeight: CGFloat = 12

    var body: some View {
        HStack(alignment: .center, spacing: 6) {
            RoundedRectangle(cornerRadius: 1.5)
                .fill(TodayColours.colour(event.mark, in: colorScheme, else: .secondary))
                .frame(width: 3, height: markHeight)
            Text(event.time)
                .font(.caption)
                .monospacedDigit()
                .foregroundStyle(Color.secondary)
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: false)
            Text(event.title)
                .font(.caption)
                .foregroundStyle(Color.primary)
                .lineLimit(1)
        }
    }
}

/// A single line, for the stale entry and the signed-out one.
private struct TodayMessageView: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.subheadline)
            .foregroundStyle(Color.secondary)
            .lineLimit(4)
    }
}

/// The snapshot's colours: a "#RRGGBB" pair, light or dark by the widget's colour scheme.
enum TodayColours {
    /// [colour]'s tone for [scheme], or [fallback] when there is none or it cannot be read.
    static func colour(_ colour: WidgetColour?, in scheme: ColorScheme, else fallback: Color) -> Color {
        guard let colour else { return fallback }
        return hex(scheme == .dark ? colour.dark : colour.light) ?? fallback
    }

    /// "#RRGGBB" in sRGB, or nil.
    static func hex(_ text: String) -> Color? {
        var digits = text
        if digits.hasPrefix("#") { digits.removeFirst() }
        guard digits.count == 6, let value = UInt32(digits, radix: 16) else { return nil }
        return Color(
            .sRGB,
            red: Double((value >> 16) & 0xFF) / 255.0,
            green: Double((value >> 8) & 0xFF) / 255.0,
            blue: Double(value & 0xFF) / 255.0,
            opacity: 1.0
        )
    }
}
