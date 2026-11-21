# EduPage event details — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An imported EduPage event's description quotes the school's messages and fee notices about it.

**Architecture:** A pure `EdupageEventDetails` in `shared-data`'s `data/school/edupage/` matches
messages to events; `EdupageTimelineParser` sets `SchoolEvent.description` from it; the source reads
120 days back.

**Spec:** `docs/superpowers/specs/2026-11-21-edupage-event-details-design.md`

## Global Constraints

- Exact folded-name match only; a repeated name also needs the date; sent before the event's day.
- Audience: child's groups (year prefix ignored) or `Rodic*`; never a personal message.
- Fee only for `ls == 7814`; text is the school's own sentence.
- No string of ours in the description; cap 2,000 characters.

## Review Focus

- Two events of one name (skating 21.12 and 27.01): a message naming only one date attaches to that one.
- A digest naming the event and other dates gives one paragraph, not the whole message.
- A noticeboard post on the event's own day is not attached.
- A withdrawn fee (`ls` 7812) attaches nothing.
- A message to this parent alone (`Rodic-<id>`) never attaches.

---

### Task 1: `EdupageEventDetails` and the parser

**Files:** Create `shared-data/src/commonMain/kotlin/com/coparently/app/data/school/edupage/EdupageEventDetails.kt`;
modify `EdupageTimelineParser.kt`, `EdupageSource.kt` (`TIMELINE_LOOKBACK_DAYS = 120`); test
`shared-data/src/commonTest/.../edupage/EdupageEventDetailsTest.kt`.

- [ ] Tests for each Review Focus line, plus: single-topic message attached whole with its date header;
  folded match ignores case and diacritics.
- [ ] Implement `EdupageEventDetails.describe(events, items, isAudience): Map<eventId, String>`.
- [ ] `:shared-data:testAndroidHostTest`, detekt, iOS test compile; PASS.

### Task 2: Documents

- [ ] CLAUDE.md item 35, `docs/legal/PRIVACY-POLICY.md`, `DPIA.md` R16, `DEVICE-CHECKLIST.md` §3.36;
  regenerate `web/privacy/` per `web/README.md`.
- [ ] Commit and push.
