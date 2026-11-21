# School hours as an option of each school connection

Owner request, 21 November 2026, after the EduPage import was checked against a live school.

## Intent

What a parent needs from the school import is its **events**: a trip, skating, a photo session —
things to prepare the child for. The lesson times ("Anna at school 08:00–11:40" on every school day)
are useful to some parents and noise to others, so they become an option. Events, tests, homework and
days off are imported as before, always, and only for the chosen child (already true: EduPage by the
child's own `childGroups`, Bakaláři by class and pupil).

Later, separately: the details of an event (what to bring, a fee), which EduPage keeps in teachers'
messages and noticeboard posts, not in the event. Not part of this change.

## Approaches (owner picked 1)

1. **A switch per connection** — "Import lesson times", stored with the child's connection, changed
   on the school import screen; EduPage and Bakaláři alike; two children, two switches. **Picked.**
2. One app-wide setting — simpler, but one answer for two children in two schools.
3. EduPage only — less code, but the two systems would behave differently for no reason.

## Design

- `SchoolConnection.importHours: Boolean`. **A new connection starts with it off; an existing one
  reads as on** (no stored key = on), so nobody using Bakaláři loses their school hours silently.
- Stored in the connection's sealed record as `"importHours": false`, written only when off, so an
  existing record's text is unchanged (the Gson parity of `SchoolConnectionCodec` holds) and an older
  build reads the record as before (it imports hours; accepted, the record is this phone's only).
- The planner takes `importHours`. Off: no school-hours event is created or updated, and every
  school-hours event this connection created for **today or later** that Room still holds is deleted
  through the ordinary delete path (tombstone, revision), and dropped from the ledger, so switching it
  on again imports them afresh. Past ones stay. Days off are not hours and stay.
- The timetable is still fetched while off: Bakaláři's days off come from it.
- The import screen shows the switch in each connection's group (`SectionRow` with an
  `AdaptiveSwitch`); changing it saves and runs the import at once, as "Update now" does.
- Strings in all five locales.

## Builder's calls (mine)

- The switch lives on the import screen only, which a parent reaches right after connecting; the
  connect flow gets no extra step.
- Deleting future hours is the second deletion an import makes; it is limited to the `hours` kind of
  this connection's own ledger, so a parent's own events and every other kind are never touched.
