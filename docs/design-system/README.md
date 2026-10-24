CoPlanly is a shared calendar for separated parents: two homes, one schedule, the children's records, the money and the conversation in one place. The design has one job — to let two people who may not get along read the same facts the same way. Calm surfaces, plain words, colour only where it means a person.

## Content fundamentals

- **Plain and calm.** Write as a neutral friend, never as either parent's advocate. Say what happened and what the reader can do: "You’re offline. Changes are kept and will sync when you’re back online."
- **"You" for the reader, names for everyone else.** The app never says "Mom" or "Dad": every parent is their own name ("Today with Alex", "Sam asks to swap Friday"). The only exception is a sticker's sentence worded from the receiver's own answer.
- **Sentence case everywhere** in English: "Event title", "Week on / week off", "Save changes". Capitalise only the first word and proper names (Google Calendar, CoPlanly).
- **Ask in the title, name the verb on the button**: "Discard changes?" → **Discard** / **Keep editing**. Never "OK".
- **Say only what is true.** No affordance may promise a feature that doesn't exist; "Sent" only once the write landed, otherwise "Queued". Facts are worded narrowly: "A schedule change or a day swap waits for your yes".
- **No emoji in UI copy.** No exclamation marks in system messages.
- **Five languages ship together**: English, Czech, German, Russian, Ukrainian. Every new string goes into all five in the same change; plan for German running ~30 % longer.
- **Times follow the reader's clock** (12 or 24 hours from the device), dates come from the locale's own formatter.

## Visual foundations

### Colour
- **Roles, not hex.** UI colour comes from the Material 3 roles in this system (`primary`, `surface-container`, `on-surface-variant`, …). Light and dark are both first-class; the prototype was authored in dark.
- **One brand colour**: `primary` — indigo #4F46E5 in light, #C2C1FF in dark. It marks the product: filled buttons, GroupLabels, row icons, today's disc, the app icon and splash.
- **`secondary` is a neutral indigo** for generic selected states (FilterChip). Never route a parent's colour through it.
- **`tertiary` (emerald) is good status as text**: "Up to date", "Agreed".
- **Errors use `error` as text** on a red-text row or a destructive dialog answer — not a filled red slab. Empty and error states are not red.

### Parent colours — a person, not a role
- Each parent picks a colour: four presets (`parent-pink`, `parent-blue`, `parent-purple`, `parent-orange`) or any hue on the wheel; slot 1 defaults to pink, slot 2 to blue.
- Each colour has four forms, and each has one job:
  - `parent-*` — **fill only**: event dots, identity borders, legend dots. Never text.
  - `parent-*-text` — the colour as a foreground (a name on `surface`), AA in its theme.
  - `parent-*-chip` + `on-chip` — a solid label carrying white text.
  - `parent-*-wash` — the custody background of a day (20 % light, 30 % dark).
- Same hue, different strength: a custody **day** is the wash, a **chip/dot/marker** is the full hue. That is what makes the cell and the chip read as one system.
- **`friend-teal`** is the calendar friend (grandparent, guardian). Teal 158°–188° is kept off the parents' wheel. A friend never takes a parent's slot or colour.
- Two parent colours under 30° apart count as one colour: the second parent is drawn in the next preset.
- Never recolour the product (logo, primary) in a parent colour.

### The calendar's layers
- **Weekend is the base layer** (`weekend` grey on every Saturday/Sunday cell, borrowed days included); custody, holiday and today are drawn **over** it, never instead of it.
- **Custody wash** runs to the grid's edge; borrowed days carry it at 60 % strength but nothing you would act on.
- **School vacation** is a 2px `outline` line along a day's bottom edge — never a fill, never a hue.
- A holiday's number is `holiday-red`; today's number sits on a `primary` disc; a pending proposal is the same hue as custody at three-fifths of its strength.

### Type
- **Onest**, four weights (400, 500, 600, 700). It covers Czech and German Latin, Russian and Ukrainian Cyrillic (і ї є ґ ʼ), ₴ and №. Any replacement must too.
- The Material 3 scale: `display-*` → `label-small`. Screen titles `title-large`; row titles `body-large`; supporting lines `body-small`; buttons `label-large`; chips `label-medium`.
- **Emphasis is a role**: use an `*-emphasized` style (one weight step up), never a free `fontWeight`.
- Text scales with the system font. Pairs that sit side by side (stat tiles, a total and its label, Delete/Edit) **stack from 1.3×**.
- **Money is never cut off**: no ellipsis or single-line clamp on an amount, a total or "who owes whom". A figure that does not fit wraps; give it the width.

### Spacing, corners, icons
- Spacing is a 4px grid plus a 2px hairline: `space-xxs` 2 · `space-xs` 4 · `space-s` 8 · `space-m` 12 · `space-l` 16 · `space-xl` 24 · `space-xxl` 32. Screen gutter `space-l`; between groups `space-xl`.
- Corners come only from the scale: `radius-xs` 8 (small controls) · `radius-sm` 12 (fields, rows) · `radius-md` 16 (groups, banners, cards) · `radius-lg` 24 (dialogs, sheets) · `radius-xl` 32 · `radius-pill`; dense marks `radius-mark` 2, tags `radius-tag` 4; chat bubbles `radius-bubble` 18 with 6 between bubbles of one run and a 4 tail.
- Icons: `icon-inline` 16 · `icon-small` 18 (buttons, chips, banners) · `icon-standard` 24 (rows, icon buttons, FABs) · `icon-large` 32 · `icon-hero` 36. Icons don't scale with the font.

### Surfaces, elevation, borders
- **Tone, not shadow**: a run of rows is one `surface-container` block (`SectionGroup`) with `outline-variant` hairlines at 40 % — never a Card per row ("double surfaces").
- The one shadow is `shadow-sticky`, under a form's pinned Save.
- An outlined control uses `outline` (3:1 as a border).

### Motion
- Three durations only: **150 ms** (fades, crossfades), **300 ms** (navigation, forms, expand), **500 ms** (a month changing). A highlight holds 1500 ms before a 300 ms fade.
- Detail screens and forms push from the right; the four tabs fade through each other. Nothing springs.
- Under "Remove animations", looping decoration (the sign-in pulse, skeleton shimmer) stands still.

### States and accessibility
- Every text pair holds 4.5:1 in both themes; parent text tones, holiday red and event titles are held to AA on every custody wash.
- Touch targets are 48px, reserved outside a drawn pill.
- Focus: a 2px `primary` ring.
- Status is never colour alone: a word or icon carries it, the colour repeats it.
- The app follows the system's contrast setting (medium and high schemes move only foregrounds).

### Layout
- Phone first: four tabs — Home, Calendar, Chat, Expenses — in a bottom bar, a navigation rail from 600px. Settings is not a tab; it opens from a gear in each tab's top bar.
- Detail screens are one readable column, at most 640px wide, centred. From 840px the tabs go to two panes (at most 1200px).
- Destructive actions sit at the bottom of their screen.

## Iconography
- **Material Icons** (Compose `Icons.Default.*`), outlined style, from Material's own set — the web previews use Material Symbols Outlined from Google Fonts as the closest match.
- No emoji as icons. No custom illustration set: an empty or first-run screen leads with one icon on a 72px `primary-container` disc (`HeroIcon`).
- The mark: see the Logos group. The app icon is the white calendar glyph on `primary`.

## Components
Use these anatomies rather than drawing your own: `SectionGroup`/`SectionRow`/`GroupLabel` for lists and settings, `PillChip` for status, `InlineBanner` (INFO/ATTENTION) for what a screen tells or asks, `EmptyState`/`ErrorState` for empty and failed lists, `StickyActionBar` for a form's Save, `AddItemButton` to add a row, `ConfirmationDialog` for a question, `ConnectivityBanner` for offline, `InviteCodeText` for a pairing code, `MiniMonth` for a small calendar, `ChangeRequestCard` and `DaySwapCard` for the Requests inbox, `ChatBubble` and `ChatDaySeparator` for a thread. The previews here are web renditions; the Android Compose code in `presentation/common/` is the source of truth.
