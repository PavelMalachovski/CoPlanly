A form's pinned primary action at the bottom of the screen, with an optional notice above it.

Source: `presentation/common/StickyActionBar.kt`.

## Anatomy
`surface` with `shadow-sticky` (8 dp elevation), `space-l` padding; a full-width filled button of **minimum** 52px height (its label grows at 200 % rather than clipping). While busy it shows a 20px spinner in the button's own content colour.

## Consumer provides
`label`, `onClick`; optional `enabled`, `busy`, `notice`.

## Rules
- Every form (event, expense, child, pet, journal) saves from here, not from a top-bar check mark.
- The window resizes for the keyboard, so the bar rides above it — don't pan.

_Web rendition hand-written from the Compose source for reference; the Android code is the truth._
