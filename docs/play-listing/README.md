# Google Play store-listing images

The phone screenshots and the feature graphic for the Play listing. They are generated from the
UI tour (`.github/workflows/ui-tour.yml`), which photographs the real app with a seeded demo family:
Alice and Bob, the children Emma and Leo, and Max the dog. Nothing here is a mock-up. The
generator only adds a headline and a device frame around what the app drew.

| File | Size | What it is |
| --- | --- | --- |
| `cs/01.png` … `cs/08.png` | 1080 × 1920 | Phone screenshots, Czech listing |
| `en/01.png` … `en/08.png` | 1080 × 1920 | Phone screenshots, English listing. **Not generated yet**, see below |
| `feature-graphic-cs.png` | 1024 × 500 | Feature graphic, Czech listing |
| `feature-graphic-en.png` | 1024 × 500 | Feature graphic, English listing |
| `icon-512.png` | 512 × 512, 32-bit PNG | The Play Store icon, both listings. Made from `icon-512.svg` |
| `icon-512.svg` | — | Its source: the launcher icon's two layers, generated from `app/src/main/res/mipmap/ic_launcher_foreground.xml` on the brand colour |

The store icon is the launcher icon, not a second drawing of it: `icon-512.svg` copies the
foreground's paths and its scale group unchanged onto `#4F46E5` (`@color/brand_primary`) and crops
the 108-unit canvas to the 72-unit area a launcher mask shows, so the calendar sits in the Play
icon at the size it sits on a home screen. It is full bleed and square; Play rounds the corners
itself. Re-render it after any change to the launcher icon, from the repository root:

```bash
pip install cairosvg pillow
python3 -c "import cairosvg; cairosvg.svg2png(url='docs/play-listing/icon-512.svg', write_to='docs/play-listing/icon-512.png', output_width=512, output_height=512)"
python3 -c "from PIL import Image; Image.open('docs/play-listing/icon-512.png').convert('RGBA').save('docs/play-listing/icon-512.png')"
```

## Regenerating

```bash
cd web-tests && npm ci && cd ..              # once: Playwright and its Chromium
node tools/play-promo/render.js --lang cs --shots <tour>/light-cs-100
node tools/play-promo/render.js --lang en --shots <tour>/light-en-100
```

`<tour>` is an unpacked UI tour: the `ui-tour` artefact, or the `ui-tour/<branch>` branch the
workflow pushes. To get a Czech tour, put `variants: light-cs-100` in `.github/ui-tour-request`.
Set `PLAYWRIGHT_BROWSERS_PATH` if Chromium lives outside Playwright's default cache.

Options:

- `--out <dir>` writes somewhere other than this folder.
- `--only slides` or `--only feature` renders one kind.
- `--preview` stamps "Preview — not for upload" across every image. It is for checking the layout
  of a language with another language's screenshots. Don't commit or upload those images.

The words live in `tools/play-promo/captions.js`, and the layout in `tools/play-promo/render.js`.
Each image is an HTML page rendered in Chromium, set in Onest (`app/src/main/res/font/`) on the
brand indigo (`CoPlanlyColors.BrandPrimary`, `#4F46E5`). The two parent colours appear only as
soft glows in the background and as the two dots on the feature graphic. The screenshot is scaled,
never stretched. The status bar is painted over in the app bar's own colour, and the system
navigation bar is cropped.

### English is waiting for an English tour

The tour folder these images were made from held `light-cs-100`, `light-de-100`, `light-de-150`,
`light-ru-100` and `light-uk-100`, with no English variant. Once a `light-en-100` folder exists, the
second command above writes `en/01.png` to `en/08.png`. Until then there are no English phone
screenshots, because slides built from Czech screens would show a Czech app under English
headlines.

The English feature graphic is committed anyway. Its phone shows only the month grid's day cells,
cropped below the weekday names, so it holds numbers and custody colours and no text in either
language.

## Play's rules for these assets

- **Phone screenshots:** PNG or JPEG, 24-bit, no alpha channel. Each side must be 320 to 3840 px,
  and the long side at most twice the short side. Play needs 2 to 8 per listing. 1080 × 1920 is
  9:16. At least 4 at 1080 px or more are needed for the listing to be eligible for
  recommendations.
- **App icon:** 512 × 512, 32-bit PNG (with alpha), at most 1 MB, full bleed.
- **Feature graphic:** exactly 1024 × 500, PNG or JPEG, 24-bit, no alpha channel.
- The renderer checks every file it writes: the exact size, 8-bit RGB, no alpha.
- **What a caption may claim:** only what the app does today (CLAUDE.md, design item 8). No "AI",
  no price, and nothing that says the export proves what happened. It is a record of what the
  parents wrote (CLAUDE.md item 26). English is in sentence case.

## Captions and the screens they show

| # | Tour screen | Czech | English |
| --- | --- | --- | --- |
| 1 | `11_calendar_month` | **Jeden kalendář péče pro obě domácnosti**<br>Každý rodič má svou barvu, takže hned vidíte, čí je který den. | **One custody calendar for both homes**<br>Each parent has a colour, so you can see whose day it is at a glance. |
| 2 | `04_home_top` | **Dnešek na první pohled**<br>U koho jsou děti a kdy je další předání. | **Today at a glance**<br>Who has the children and when the next handover is. |
| 3 | `01_home_dialog_swap` | **Výměna dne, jen když souhlasíte oba**<br>Druhý rodič návrh přijme, nebo odmítne. | **Swap a day, only when you both agree**<br>The other parent accepts or declines the request. |
| 4 | `19_chat_thread` | **Zprávy, které nikdo nepřepíše**<br>Odeslanou zprávu nelze upravit ani smazat. | **Messages nobody can rewrite**<br>A sent message can’t be edited or deleted. |
| 5 | `22_expenses_list` | **Společné výdaje bez dohadování**<br>Kdo co zaplatil a kdo komu kolik dluží. | **Shared expenses, clearly split**<br>Who paid for what, and who owes whom. |
| 6 | `34_child_detail` | **Vše o dětech na jednom místě**<br>Léky, alergie, kroužky i nouzové kontakty. | **Everything about the kids in one place**<br>Medication, allergies, activities and emergency contacts. |
| 7 | `50_export_scrolled_1` | **Záznam pro advokáta nebo mediátora**<br>PDF nebo CSV s tím, co jste si zapsali a napsali. | **A record for your lawyer or mediator**<br>A PDF or CSV of what you both recorded and wrote. |
| 8 | `39_custody_setup` | **Rozvrh péče podle vaší dohody**<br>Střídání po týdnu, 2-2-3, 3-4-4-3 i vlastní vzor. | **A schedule that fits your agreement**<br>Week on / week off, 2-2-3, 3-4-4-3 or a pattern of your own. |

Feature graphic: **CoPlanly**, then "Sdílený kalendář pro rodiče, kteří spolu nežijí" with
"Péče · Výdaje · Domluva", or in English "The shared calendar for parents who live apart" with
"Custody · Expenses · Messages". The phone shows `11_calendar_month`.

### Why these screens

- **Left out for known defects:**
  - `24_expenses_analytics`: the "+" button sits over the total.
  - `14_calendar_filters`: its chips are in English on a Czech screen.
  - `12_calendar_week`: its event titles are cut off.
- **`50_export_scrolled_1`, not `49_export`:** the scrolled screen shows the PDF and CSV actions,
  and the paragraph about the record ID and the file's fingerprint.
- **`39_custody_setup`, not `43_parenting_plan`:** the plan screen is mostly long question text,
  and its sample answer is in English.
- **Sample text is in English in the Czech tour:** the seeded chat messages and Emma's
  medication, activity and notes (`UiTourSeed`). That text is data the parents typed, not the
  app's interface. A fully Czech listing needs a Czech seed.
