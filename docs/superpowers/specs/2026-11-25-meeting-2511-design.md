# Meeting of 25 November 2026: seven fixes

The owner and a tester met on 25 November 2026, and the owner approved the result on the canvas
board "Встреча 25.11: правки" (`Meeting2511.dc.html`). The board is the design. This document
records what it decided and the calls made while turning it into code.

## Why

All seven points come from a tester using the app with a real family. Each is a place where the
app made her stop and ask what it meant:
- a child's photo that could not be opened;
- a colour she picked that the app drew differently, with no word of why;
- a day swap phrased as dates and arrows rather than "who has the child when";
- a switch ("Lesson times") whose effect was unclear;
- a reminder the proposer never saw being set;
- a school address that looked optional but was not;
- a history list where both parents' requests looked the same.

## The seven changes

1. **Home: a tap on a child's or pet's avatar opens its photo full screen.** This is only on
   Home's header (`BrandHeader` → `FamilyAvatarsAndGear` without a filter). On the Calendar the
   avatars stay the member filter.
   - The viewer is the existing `FullScreenImageDialog` (zoom, ✕, a tap closes).
   - An avatar with no photo this viewer may see does nothing and is not a button.
   - The "+n" disc does nothing.
   - TalkBack reads each avatar with a photo as a button ("Open {name}'s photo").
2. **Same colour as the co-parent: a notice in the colour picker.** Today `ParentPalette.of`
   silently draws one parent in another colour when the two colours are closer than 30°.
   - While the colour being picked would be moved for this parent, the picker shows a notice above
     the grid. Shown only to the parent who is moved, and live as the choice changes. Wording:
     "{Co-parent} also chose {colour}. So that your days look different, you are shown in
     {shown colour} in your family. Pick another colour to be shown in your own." When either
     colour is a custom hue with no name, use the sentence without the colour names.
   - The co-parent's swatch carries their first name under it, whoever is moved.
   - The notice uses the same rule `ParentPalette.of` uses, never a copy of it.
3. **Day swap phrased by who has the child.** The Home pop-up, the inbox card and the push lead
   with the children's names and the days.
   - English: "Lenička will be with you on Fri 13 and Sat 14 Nov and with Pavel on Fri 20 Nov",
     heading "Lenička with you on Fri 13 and Sat 14 Nov?", push body "Lenička with you on Fri 13
     and Sat 14 Nov, with Pavel on Fri 20 Nov."
   - Two children: "Lenička and Sašík". The verb follows the count (plurals keyed on the number of
     children).
   - No children: "Fri 13 and Sat 14 Nov will be with you".
   - Children are the family on screen's children, never its pets.
   - A child with a schedule of their own is left out (item 33: a swap does not move them).
   - The line "Pavel proposes this swap · answer by …" stays under the sentence. The per-day chips
     and "Answer day by day" stay as they are.
4. **EduPage "Lesson times": say what on and off mean, under the switch.**
   - On: each school day gets a "{child} at school" block from the first lesson to the last.
   - Off: no such blocks; school events, tests, homework and days off still come in.
   - The confirm screen's "Grades, homework and individual lessons are not imported" is wrong for
     EduPage, which imports homework. For EduPage, say "Grades are not imported", and list tests
     and homework among what comes in. Bakaláři keeps today's lines.
5. **Day swap sheet: the deadline and reminders are always shown.** Under the rows, in place of the
   "Answer by" chip, the sheet shows one line: "⏱ Answer by {time} · up to {n} reminders". Under it
   is a second line: "{Co-parent} is reminded automatically until they answer. Tap to change the
   date." A tap opens today's `AnswerByField` in place. The count is the plan `AnswerByField`'s
   consequences line already uses. When the offer gives no deadline (too soon), the line says
   what `answer_by_too_soon` says.
6. **EduPage: the school's address is required.** The label loses "(optional)". The hint becomes
   "Required — the address you open EduPage at, e.g. zsx.edupage.org". Connect is disabled while the
   field is blank. The existing format check stays.
7. **Requests history: each request in its author's colour.**
   - Each history row carries a 5 dp bar on its leading edge in the requester's parent colour
     (`ParentColors.fill` of `askedBy`'s slot).
   - The row's meta line starts with "Your request" / "{name}'s request" in that parent's text colour
     (`ParentColors.text`), then " · " and today's who-answered-when text.
   - A small legend under the History heading names both colours: "you" and the co-parent.
   - The status chips stay neutral. Status colour is a theme role and parent colour is a person
     (design item 5).
   - Only the History section changes.

## Calls made here

- **Names are never declined.** Czech, Russian and Ukrainian decline a name ("u Pavla", "у Павла"),
  and no rule can decline an arbitrary name. The translations keep every name in the nominative.
  - cs: "Lenička bude u tebe v pá 13. a so 14. 11.; Pavel má pá 20. 11."
  - ru: "Леночка будет у тебя в пт 13 и сб 14 нояб.; у Павла — …" is not allowed. Write "Павел —
    пт 20 нояб." instead.
  - German and English need no workaround ("bei Pavel", "with Pavel").
- **The push carries the children's first names** in one new field, `childNames` (names joined by
  a line break, at most 200 characters, bounded in `firestore.rules` like every client key). The
  receiving phone words the sentence (item 15).
  - An older sender's push has no field and keeps today's sentence.
  - A field, not a type, so the four-place rule does not apply.
  - This is the same kind of data a chat preview already carries. The privacy policy and the
    records of processing say pushes may carry a child's first name.
- **The notice in item 2 reads `ParentPalette.of`** for the parent's slot. Which colour is moved is
  that rule's answer, and the picker does not restate it.

## Non-goals

- No change to how colours collide or which parent is moved.
- No new deadline rules and no server change. The reminders still need `firebase deploy --only
  functions`, which is the owner's step.
- No change to the Calendar's avatars, the swap selection or the swap's stored form.
