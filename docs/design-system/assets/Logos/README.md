CoPlanly's mark is the launcher icon's glyph: a calendar with binder rings and a header bar, and in place of the grid of days two adults holding a child by the hands (November 2026). These files, the launcher icon, the iPhone icon, both splash screens and the sign-in logo are all written by `tools/brand/generate-brand-assets.py` from one drawing; never edit them by hand.

- `coplanly-app-icon.svg` — the full app icon: the white glyph on `primary` (#4F46E5), as the launcher and the system splash draw it. Square; the launcher applies its own mask.
- `coplanly-mark-indigo.svg` — the glyph alone in `primary` (#4F46E5), for light grounds.
- `coplanly-mark-white.svg` — the glyph alone in white, for `primary` or dark grounds.

Single-ink files: their ink is fixed in the file (they are drawn through `<img>` and cannot inherit a colour). There is no wordmark file: set "coplanly." in Onest Bold beside the mark, the full stop in `primary`, as the app's header does (`BrandWordmark`); the splash screens draw it as outlines the generator makes from the font. Never recolour the mark in a parent's colour — pink and blue identify people, not the product.
