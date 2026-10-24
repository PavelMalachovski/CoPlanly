Material 3 buttons as CoPlanly uses them: filled for the one primary action, tonal for adding, text for dialogs, error-text for destructive answers.

## When to use
- `filled` — the screen's one primary action (EmptyState's action, a form's Save). One per view.
- `tonal` — adding a row to a list a form holds; use **AddItemButton**.
- `text` — dialog answers and banner actions.
- `text-error` — a destructive answer ("Discard", "Unpair"). Never a filled red slab.
- `outlined` — a secondary choice beside a filled one.

## Consumer provides
`children` (label, sentence case), optional `icon` (Material Symbols name, drawn at `icon-small`), `onClick`, `disabled`.

## Don't
- Don't Title Case labels: "Save changes", not "Save Changes".
- Don't put two filled buttons side by side.

_Web rendition hand-written from the Compose source for reference; the Android code is the truth._
