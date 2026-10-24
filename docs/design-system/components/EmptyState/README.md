Every empty list renders through this: a HeroIcon on a tonal disc, a title, an optional description and an optional primary action.

Source: `DesignSystem.kt` → `EmptyState`, `HeroIcon`.

## Anatomy
72px `primary-container` disc with an `icon-hero` (36px) glyph in `on-primary-container`; `title-medium` title; `body-medium` description in `on-surface-variant`; a filled Button. Padding `space-xxl` × `space-xl`, gaps `space-m`, centred.

## Consumer provides
`icon`, `title`; optional `description`, `actionLabel` + `onAction` (only when the action does something real).

## Rules
- Don't draw a bespoke column or a `Card { Text }` for an empty list.
- It scrolls when its height is bounded, so a tall state stays reachable at 200 % text.

_Web rendition hand-written from the Compose source for reference; the Android code is the truth._
