The single offline line in the root top bar — appears when the phone is offline, announced politely to screen readers.

Source: `presentation/common/ConnectivityBanner.kt`.

`secondary-container` fill, `on-secondary-container` text, `label-medium`, a cloud-off icon at `icon-small`, `space-s` × `space-l` padding. It expands in over `MEDIUM_MS` (300 ms) and fades out over `SHORT_MS` (150 ms). There is one, at the root — screens don't add their own.

_Web rendition hand-written from the Compose source for reference; the Android code is the truth._
