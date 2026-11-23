# One mark on both platforms: the family in the calendar

23 November 2026. Owner decisions taken on the brainstorming canvas (boards A/B/C, C1–C3, the
"canvas" board).

## Why

The sign-in screen showed a different logo on each platform: it drew Material's `ChildCare`
through `AppIcon`, which the iPhone swaps for the SF Symbol `figure.and.child.holdinghands`
("an adult holding a child by the hand") and Android draws as a baby's face. The owner liked the
iPhone's picture and wanted one logo everywhere, with a second adult: the app is for two parents.
The app icon (a white calendar with a grid of days) and the splash (that calendar on Android, a
blank white screen on the iPhone) changed with it.

## What

- **The mark (board C3).** The calendar keeps its binder rings, frame and white header bar; in
  place of the grid of days, two adults hold a child by the hands. White on the brand colour
  `#4F46E5`. All white, not the parents' pink and blue: a parent may choose any colour, and the
  product's mark is not a person.
- **The app icon** is the mark, on Android (adaptive foreground and the Android 13+ monochrome
  icon) and on the iPhone, and the Play Store icon and feature graphics carry it.
- **The splash** on both platforms: the brand colour, the mark in the middle, "coplanly." in
  Onest Bold at the bottom (the full stop in `#C2C1FF`). At the bottom on both because Android
  12+'s system splash takes text only as its bottom branding image, and the owner chose the same
  layout on both rather than a centred wordmark on the iPhone alone. No second, Compose splash
  (design item 11).
- **The sign-in screen** draws the mark (`Res.drawable.brand_mark`, tinted `BrandPrimary`, with
  its pulse) and "coplanly." (`BrandWordmark`, the header's) in place of `ChildCare` and the
  "CoPlanly" title.

## Not

- No SF Symbol and no Material icon as the mark: Apple's licence forbids SF Symbols in app icons
  and logos, and an icon `AppIcon` maps is exactly what made the two platforms differ.
- The notification icon (`ic_notification`, a small calendar) is unchanged.
- The Home header's wordmark is unchanged; it is the same composable the sign-in screen now uses.

## Approaches

1. **(Chosen)** Draw the mark in code, once, and generate every copy:
   `tools/brand/generate-brand-assets.py` holds the drawing as filled shapes in the adaptive-icon
   grid and writes the Android vectors, the Compose resource, the iPhone icon and launch images,
   the Play icon, the design-system logos and the site's inline icon; the wordmark is Onest Bold
   turned into outlines with fontTools. Crisp at every size, one edit updates every place.
2. Draw it in Figma and import exported files: possibly livelier, but every change is a manual
   re-export, and the owner would have had to draw it.
3. A generated raster image: dropped, since Android's monochrome icon and small sizes need a
   clean vector.

## Calls made in building it

- The mark sits at 0.56 of the 108 grid in the launcher layers, so the frame's rounded corners
  stay inside the 66 dp circle a launcher may mask to; the iPhone launch screen draws it at the
  width Android's splash does (127 pt).
- The iPhone launch screen is a storyboard (`UILaunchStoryboardName`), because `UILaunchScreen`
  can centre one image only.
- The feature graphics were patched in place (the logo tile only) rather than re-rendered from a
  UI tour, so their phone screenshot is unchanged; `render.js` draws the new mark on the next full
  render.
- The duplicate `default_web_client_id` fix (`3e53c69c`, never merged) is included, because no
  build with `google-services.json` (every local and release build) passed without it.
