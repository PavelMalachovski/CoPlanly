EmptyState's anatomy with a Retry action — for a screen whose content failed to load. It is not red.

Source: `presentation/common/ErrorState.kt`.

## Consumer provides
`message` (a localised sentence — never an exception's `message`), optional `onRetry`.

Uses the error-outline glyph on the same `primary-container` disc; the tone stays calm because a separated parent reading it is often already stressed.

_Web rendition hand-written from the Compose source for reference; the Android code is the truth._
