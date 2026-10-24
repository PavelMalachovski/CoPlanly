A Material alert dialog with two text-button answers; a destructive confirm is in the `error` colour.

Source: `presentation/common/ConfirmationDialog.kt`.

## Anatomy
`radius-lg`, `headline-small` title, `body-large` message, both answers text buttons (`text` / `text-error`).

## Consumer provides
`title`, `message`, `confirmText`, `dismissText`, `onConfirm`, `onDismiss`, `destructive`.

## Copy
Ask the question in the title ("Discard changes?"), say the consequence in the message, name the verb on the button ("Discard", not "OK").

_Web rendition hand-written from the Compose source for reference; the Android code is the truth._
