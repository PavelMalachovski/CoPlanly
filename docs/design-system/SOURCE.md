# CoPlanly design system — source files

The files in this folder are what the published Design System artifact holds
(https://claude.ai/artifact/AozpnoNbZ9ffWUZKhSLrnX), kept here so they are versioned with the code.

- `README.md` — the brand book: content, colour, type, spacing, motion, iconography rules.
- `tokens.json` — every token, light and dark, with a usage note. Written from
  `app/src/main/java/com/coparently/app/presentation/theme/` (`Color.kt`, `Theme.kt`, `ParentPalette.kt`,
  `Type.kt`, `Spacing.kt`, `Shape.kt`, `IconSizes.kt`).
- `components/` — web renditions of `presentation/common/`, `changerequests/` and `chat/` components
  (`bundle.js`, `bundle.css`, `index.d.ts`) with a guideline `README.md` and a `preview.html` each.
- `assets/Logos/` — the app icon and mark, converted path for path from `res/mipmap/ic_launcher_foreground.xml`.
- `design-system.json` — the artifact's index (names the uploaded logo files).

The Kotlin theme is the source of truth: when a token changes there, change `tokens.json` here in the
same pull request. The Onest font files are not duplicated: they live in `app/src/main/res/font/`.
