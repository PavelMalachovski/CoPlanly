What a screen tells or asks its reader, inline: INFO for a fact, ATTENTION for an ask.

Source: `presentation/common/InlineBanner.kt`, `BannerTone.kt`.

## Tones
- `info` — `surface-container` fill, `primary` icon, `on-surface` title, `on-surface-variant` body.
- `attention` — `primary-container` fill, `on-primary-container` text. Use when the reader has something to answer.

## Consumer provides
`text`; optional `title`, `icon` (drawn at `icon-small`), `tone`, `actions` (text buttons; they wrap in a flow row).

## Don't
- Don't add a coloured left border or a red tone — errors go to `ErrorState` or a snackbar.
- The calendar's compact one-line banners over the grid are a deliberate exception and use their own anatomy.

_Web rendition hand-written from the Compose source for reference; the Android code is the truth._
