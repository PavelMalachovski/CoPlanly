#!/usr/bin/env python3
"""Writes every copy of the CoPlanly mark and the splash wordmark from one drawing.

The mark is a calendar: binder rings, a frame, a white header bar, and in place of the grid of
days two adults holding a child by the hands (owner decision, 23 November 2026, board "C3" in
docs/superpowers/specs/2026-11-23-family-brand-mark-design.md). It is the app's icon on both
platforms, the logo on the sign-in screen and the centre of both splash screens, and the
wordmark "coplanly." sits at the bottom of both splash screens.

The drawing lives here, in `mark_shapes()`, as filled shapes only (no strokes), in the
108 x 108 grid of an Android adaptive icon. Everything else is generated:

  app/src/main/res/mipmap/ic_launcher_foreground.xml      the launcher foreground (white)
  app/src/main/res/drawable/ic_launcher_monochrome.xml    Android 13+ themed icon
  app/src/main/res/drawable/splash_wordmark.xml           Android 12+ splash branding image
  shared-ui/src/commonMain/composeResources/drawable/brand_mark.xml    the sign-in logo
  iosApp/CoPlanly/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png  the iPhone icon
  iosApp/CoPlanly/Assets.xcassets/LaunchMark.imageset/                 iPhone launch screen
  iosApp/CoPlanly/Assets.xcassets/LaunchWordmark.imageset/             iPhone launch screen
  docs/play-listing/icon-512.svg and icon-512.png                      the Play Store icon
  docs/design-system/assets/Logos/*.svg                                the design system's logos
  web/index.html                                                       the site's header icon

The wordmark is Onest Bold (the font the app's header sets "coplanly." in), turned into
outlines with fontTools so no platform needs the font to draw the splash.

PNGs are drawn by headless Chrome (or Chromium): set CHROME to its binary if it is not at the
macOS default. fontTools and Pillow come from a virtual environment of their own:

    python3 -m venv /tmp/brand-venv && /tmp/brand-venv/bin/pip install fonttools pillow
    /tmp/brand-venv/bin/python tools/brand/generate-brand-assets.py

Change the mark here, then run this; never edit a generated file by hand.
"""
import io
import json
import math
import os
import pathlib
import re
import subprocess
import tempfile

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent.parent
BRAND = "#4F46E5"        # @color/brand_primary, CoPlanlyColors.BrandPrimary
WORDMARK_DOT = "#C2C1FF"  # the dark theme's primary: the wordmark's "." on the brand colour
WHITE = "#FFFFFF"
FONT = ROOT / "shared-ui/src/commonMain/composeResources/font/onest_bold.ttf"

# How large the mark sits in the 108 grid of the launcher layers. An adaptive icon may be
# masked to a circle of the inner 66 dp; at this scale the farthest point of the frame's
# rounded corner stays inside it.
LAUNCHER_SCALE = 0.56
# The Android splash draws the foreground layer at 288 dp, so the mark is this wide there;
# the iPhone launch screen draws it at the same size in points.
SPLASH_MARK_WIDTH = 288 * LAUNCHER_SCALE * (85 / 108)
# The splash wordmark's cap-to-baseline height, in dp and points.
SPLASH_WORDMARK_HEIGHT = 30
# Android's splash branding image is 200 x 80 dp.
BRANDING_SIZE = (200, 80)


def fmt(v):
    """A coordinate with at most three decimals and no trailing zeros."""
    s = f"{v:.3f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def capsule(x1, y1, x2, y2, r):
    """A stroke from (x1, y1) to (x2, y2) with round caps, as a filled outline."""
    dx, dy = x2 - x1, y2 - y1
    length = math.hypot(dx, dy)
    nx, ny = -dy / length * r, dx / length * r
    a = (x1 + nx, y1 + ny)
    b = (x2 + nx, y2 + ny)
    c = (x2 - nx, y2 - ny)
    d = (x1 - nx, y1 - ny)
    return (f"M{fmt(a[0])},{fmt(a[1])} L{fmt(b[0])},{fmt(b[1])} "
            f"A{fmt(r)},{fmt(r)} 0 0 0 {fmt(c[0])},{fmt(c[1])} "
            f"L{fmt(d[0])},{fmt(d[1])} A{fmt(r)},{fmt(r)} 0 0 0 {fmt(a[0])},{fmt(a[1])} Z")


def circle(cx, cy, r):
    return (f"M{fmt(cx - r)},{fmt(cy)} A{fmt(r)},{fmt(r)} 0 1 0 {fmt(cx + r)},{fmt(cy)} "
            f"A{fmt(r)},{fmt(r)} 0 1 0 {fmt(cx - r)},{fmt(cy)} Z")


def rounded_rect(x, y, w, h, r):
    return (f"M{fmt(x + r)},{fmt(y)} H{fmt(x + w - r)} A{fmt(r)},{fmt(r)} 0 0 1 {fmt(x + w)},{fmt(y + r)} "
            f"V{fmt(y + h - r)} A{fmt(r)},{fmt(r)} 0 0 1 {fmt(x + w - r)},{fmt(y + h)} "
            f"H{fmt(x + r)} A{fmt(r)},{fmt(r)} 0 0 1 {fmt(x)},{fmt(y + h - r)} "
            f"V{fmt(y + r)} A{fmt(r)},{fmt(r)} 0 0 1 {fmt(x + r)},{fmt(y)} Z")


def mark_shapes():
    """The mark in the 108 grid, centred on (54, 54): a list of (pathData, evenOdd)."""
    dy = -1  # the drawing was laid out around y = 55; this centres it
    shapes = []
    # Binder rings.
    shapes.append((capsule(38, 16 + dy, 38, 27 + dy, 3), False))
    shapes.append((capsule(70, 16 + dy, 70, 27 + dy, 3), False))
    # The frame: an outer and an inner rounded rectangle, filled even-odd into a ring.
    outer = rounded_rect(11.5, 18.5 + dy, 85, 79, 16.5)
    inner = rounded_rect(16.5, 23.5 + dy, 75, 69, 11.5)
    shapes.append((outer + " " + inner, True))
    # The header bar, reaching one unit under the frame so no hairline shows between them.
    y0, y1 = 22.5 + dy, 38 + dy
    shapes.append((f"M28,{fmt(y0)} H80 A12.5,12.5 0 0 1 92.5,{fmt(y0 + 12.5)} V{fmt(y1)} "
                   f"H15.5 V{fmt(y0 + 12.5)} A12.5,12.5 0 0 1 28,{fmt(y0)} Z", False))

    # Two adults holding a child by the hands, drawn at full size and placed into the frame.
    k, cx, cy = 0.6, 54, 66 + dy

    def p(x, y):
        return cx + k * (x - 54), cy + k * (y - 54)

    def limb(x1, y1, x2, y2, width):
        (a, b), (c, d) = p(x1, y1), p(x2, y2)
        shapes.append((capsule(a, b, c, d, width * k / 2), False))

    def head(x, y, r):
        a, b = p(x, y)
        shapes.append((circle(a, b, r * k), False))

    for side in (-1, 1):  # the left and the right adult, mirrored about x = 54
        x = 54 + side * 20
        head(x, 30, 7)
        limb(x, 42, x, 61, 10)
        limb(x, 61, x - 5, 79, 7)
        limb(x, 61, x + 5, 79, 7)
        limb(x, 46, x + side * 8, 60, 6)       # the outer arm, from the shoulder
        limb(x, 46, 54 + side * 7, 60, 6)      # the arm holding the child's hand
    head(54, 50, 5.5)
    limb(54, 59, 54, 69, 8)
    limb(54, 69, 50.5, 79, 5.5)
    limb(54, 69, 57.5, 79, 5.5)
    limb(54, 61, 47, 60, 5)
    limb(54, 61, 61, 60, 5)
    return shapes


# The mark's bounds in the 108 grid (for the tight copies): x 11.5..96.5, y 12..96.5.
MARK_BOX = (11.5, 12.0, 85.0, 84.5)


def vector_paths(shapes, colour, indent):
    out = []
    for data, even_odd in shapes:
        rule = ' android:fillType="evenOdd"' if even_odd else ""
        out.append(f'{indent}<path android:fillColor="{colour}"{rule}\n'
                   f'{indent}    android:pathData="{data}" />')
    return "\n".join(out)


EVEN_ODD = ' fill-rule="evenodd"'


def svg_paths(shapes, colour):
    return "".join(
        f'<path fill="{colour}"{EVEN_ODD if eo else ""} d="{d}"/>' for d, eo in shapes)


GENERATED = "Generated by tools/brand/generate-brand-assets.py; edit the drawing there, never here."


def launcher_xml(shapes, colour, what):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!--
  {what}
  The CoPlanly mark: a calendar whose grid of days is two adults holding a child by the hands.
  {GENERATED}
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group
        android:pivotX="54"
        android:pivotY="54"
        android:scaleX="{LAUNCHER_SCALE}"
        android:scaleY="{LAUNCHER_SCALE}">
{vector_paths(shapes, colour, "        ")}
    </group>
</vector>
'''


def tight_mark_xml(shapes):
    x, y, w, h = MARK_BOX
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!--
  The CoPlanly mark, cropped to its own bounds, for the sign-in screen (tinted there).
  {GENERATED}
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{fmt(w)}dp"
    android:height="{fmt(h)}dp"
    android:viewportWidth="{fmt(w)}"
    android:viewportHeight="{fmt(h)}">
    <group
        android:translateX="{fmt(-x)}"
        android:translateY="{fmt(-y)}">
{vector_paths(shapes, WHITE, "        ")}
    </group>
</vector>
'''


def wordmark_glyphs():
    """'coplanly' and '.' as SVG path data in font units (y up), with the total advance."""
    font = TTFont(FONT)
    glyph_set = font.getGlyphSet()
    cmap = font.getBestCmap()
    head = font["head"]
    os2 = font["OS/2"]
    x = 0
    parts = {"letters": [], "dot": []}
    for ch in "coplanly.":
        name = cmap[ord(ch)]
        pen = SVGPathPen(glyph_set)
        glyph_set[name].draw(TransformPen(pen, (1, 0, 0, -1, x, 0)))
        parts["dot" if ch == "." else "letters"].append(pen.getCommands())
        x += glyph_set[name].width
    ink_top = os2.sCapHeight if getattr(os2, "sCapHeight", 0) else head.unitsPerEm * 0.7
    return " ".join(parts["letters"]), " ".join(parts["dot"]), x, ink_top, font


def wordmark_box(font, letters_and_dot):
    """The wordmark's ink bounds in font units, measured with fontTools' bounds pen."""
    from fontTools.pens.boundsPen import BoundsPen
    glyph_set = font.getGlyphSet()
    cmap = font.getBestCmap()
    pen = BoundsPen(glyph_set)
    x = 0
    for ch in letters_and_dot:
        name = cmap[ord(ch)]
        glyph_set[name].draw(TransformPen(pen, (1, 0, 0, -1, x, 0)))
        x += glyph_set[name].width
    return pen.bounds  # xMin, yMin, xMax, yMax with y down


def wordmark_geometry(height):
    """Wordmark path data scaled so its x-height-to-descender ink is [height] units tall.

    Returns (letters, dot, width, height) with the ink's top-left at (0, 0).
    """
    letters, dot, _advance, _cap, font = wordmark_glyphs()
    x0, y0, x1, y1 = wordmark_box(font, "coplanly.")
    scale = height / (y1 - y0)
    return letters, dot, (x0, y0, scale), (x1 - x0) * scale, height


def scaled_group(transform):
    x0, y0, s = transform
    return f'translate(0 0) scale({fmt(s)}) translate({fmt(-x0)} {fmt(-y0)})'


def splash_wordmark_xml():
    letters, dot, (x0, y0, s), w, h = wordmark_geometry(SPLASH_WORDMARK_HEIGHT)
    bw, bh = BRANDING_SIZE
    tx = (bw - w) / 2
    ty = (bh - h) / 2
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!--
  "coplanly." in Onest Bold as outlines: the Android 12+ splash screen's branding image
  (200 x 80 dp, drawn at the bottom of the screen; values-v31/themes.xml).
  {GENERATED}
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{bw}dp"
    android:height="{bh}dp"
    android:viewportWidth="{bw}"
    android:viewportHeight="{bh}">
    <group
        android:translateX="{fmt(tx)}"
        android:translateY="{fmt(ty)}">
        <group
            android:scaleX="{fmt(s)}"
            android:scaleY="{fmt(s)}">
            <group
                android:translateX="{fmt(-x0)}"
                android:translateY="{fmt(-y0)}">
                <path android:fillColor="{WHITE}"
                    android:pathData="{letters}" />
                <path android:fillColor="{WORDMARK_DOT}"
                    android:pathData="{dot}" />
            </group>
        </group>
    </group>
</vector>
'''


def chrome():
    return os.environ.get("CHROME", "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")


def render_png(svg, width, height):
    """An RGBA image of [svg] at [width] x [height] px, drawn by headless Chrome."""
    with tempfile.TemporaryDirectory() as tmp:
        page = pathlib.Path(tmp) / "page.html"
        shot = pathlib.Path(tmp) / "shot.png"
        page.write_text(
            "<!doctype html><html><head><style>html,body{margin:0;padding:0;background:transparent}"
            f"svg{{display:block;width:{width}px;height:{height}px}}</style></head><body>{svg}</body></html>",
            encoding="utf-8")
        subprocess.run(
            [chrome(), "--headless=new", "--disable-gpu", "--hide-scrollbars",
             "--force-device-scale-factor=1", "--default-background-color=00000000",
             f"--window-size={width},{height}", f"--screenshot={shot}", page.as_uri()],
            check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        return Image.open(io.BytesIO(shot.read_bytes())).convert("RGBA").crop((0, 0, width, height))


def icon_svg(shapes, size=None):
    """The launcher icon as a square: the brand colour and the mark, cropped to the 72 dp a mask shows."""
    dims = f' width="{size}" height="{size}"' if size else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72"{dims}>'
            f'<rect x="0" y="0" width="108" height="108" fill="{BRAND}"/>'
            f'<g transform="translate(54 54) scale({LAUNCHER_SCALE}) translate(-54 -54)">'
            f'{svg_paths(shapes, WHITE)}</g></svg>')


def mark_svg(shapes, colour):
    x, y, w, h = MARK_BOX
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{fmt(x)} {fmt(y)} {fmt(w)} {fmt(h)}" '
            f'width="{fmt(w)}" height="{fmt(h)}">{svg_paths(shapes, colour)}</svg>')


def wordmark_svg(height):
    letters, dot, (x0, y0, s), w, h = wordmark_geometry(height)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {fmt(w)} {fmt(h)}">'
            f'<g transform="scale({fmt(s)}) translate({fmt(-x0)} {fmt(-y0)})">'
            f'<path fill="{WHITE}" d="{letters}"/><path fill="{WORDMARK_DOT}" d="{dot}"/></g></svg>'), w, h


def write(rel, text):
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    print("wrote", rel)


def write_png(rel, image, flatten=None):
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    if flatten:
        rgb = tuple(int(flatten[i:i + 2], 16) for i in (1, 3, 5))
        base = Image.new("RGBA", image.size, rgb + (255,))
        base.alpha_composite(image)
        base.convert("RGB").save(path, optimize=True)
    else:
        image.save(path, optimize=True)
    print("wrote", rel)


def image_set(rel_dir, svg, width_pt, height_pt):
    """An asset-catalog image set at @1x, @2x and @3x."""
    images = []
    for scale in (1, 2, 3):
        name = f"{pathlib.Path(rel_dir).stem}@{scale}x.png"
        png = render_png(svg, round(width_pt * scale), round(height_pt * scale))
        write_png(f"{rel_dir}/{name}", png)
        images.append({"idiom": "universal", "filename": name, "scale": f"{scale}x"})
    write(f"{rel_dir}/Contents.json",
          json.dumps({"images": images, "info": {"author": "xcode", "version": 1}}, indent=2) + "\n")


def main():
    shapes = mark_shapes()

    write("app/src/main/res/mipmap/ic_launcher_foreground.xml",
          launcher_xml(shapes, WHITE, "Adaptive-icon foreground, white on @drawable/ic_launcher_background; "
                                      "also the Android 12+ splash icon."))
    write("app/src/main/res/drawable/ic_launcher_monochrome.xml",
          launcher_xml(shapes, "#000000", "Android 13+ themed icon: the launcher tints this shape."))
    write("shared-ui/src/commonMain/composeResources/drawable/brand_mark.xml", tight_mark_xml(shapes))
    write("app/src/main/res/drawable/splash_wordmark.xml", splash_wordmark_xml())

    # The iPhone icon: the launcher's visible 72 dp at 1024 px, no alpha (the App Store refuses one).
    write_png("iosApp/CoPlanly/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png",
              render_png(icon_svg(shapes), 1024, 1024), flatten=BRAND)

    # The iPhone launch screen: the mark at the size the Android splash draws it, the wordmark under it.
    x, y, w, h = MARK_BOX
    mark_w = SPLASH_MARK_WIDTH
    image_set("iosApp/CoPlanly/Assets.xcassets/LaunchMark.imageset", mark_svg(shapes, WHITE),
              mark_w, mark_w * h / w)
    svg, ww, wh = wordmark_svg(SPLASH_WORDMARK_HEIGHT)
    image_set("iosApp/CoPlanly/Assets.xcassets/LaunchWordmark.imageset", svg, ww, wh)

    # The Play Store icon: full bleed and square, Play applies its own mask.
    play_svg = ('<?xml version="1.0" encoding="UTF-8"?>\n'
                '<!-- The Play Store icon (512 x 512): the launcher icon cropped to the 72 x 72 a mask shows.\n'
                f'     {GENERATED} -->\n' + icon_svg(shapes, 512) + "\n")
    write("docs/play-listing/icon-512.svg", play_svg)
    write_png("docs/play-listing/icon-512.png", render_png(icon_svg(shapes), 512, 512))

    # The design system's logos.
    write("docs/design-system/assets/Logos/coplanly-app-icon.svg",
          f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="108" height="108">'
          f'<path fill="{BRAND}" d="M0,0 H108 V108 H0 Z"/>'
          f'<g transform="translate(54 54) scale({LAUNCHER_SCALE}) translate(-54 -54)">'
          f'{svg_paths(shapes, WHITE)}</g></svg>\n')
    write("docs/design-system/assets/Logos/coplanly-mark-white.svg", mark_svg(shapes, WHITE) + "\n")
    write("docs/design-system/assets/Logos/coplanly-mark-indigo.svg", mark_svg(shapes, BRAND) + "\n")

    # The site's header icon, inline (the home page makes no request; web-tests holds it to that).
    index = ROOT / "web/index.html"
    html = index.read_text(encoding="utf-8")
    inline = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" aria-hidden="true" focusable="false">'
              f'<rect x="0" y="0" width="108" height="108" fill="{BRAND}"/>'
              f'<g transform="translate(54 54) scale({LAUNCHER_SCALE}) translate(-54 -54)" fill="{WHITE}">'
              + "".join(f'<path{EVEN_ODD if eo else ""} d="{d}"/>' for d, eo in shapes)
              + "</g></svg>")
    marker = "<!-- The app's icon, as on Google Play: docs/play-listing/icon-512.svg. -->"
    pattern = re.compile(re.escape(marker) + r"\s*<svg.*?</svg>", re.S)
    if not pattern.search(html):
        raise SystemExit("the icon marker was not found in web/index.html")
    html = pattern.sub(lambda _m: marker + "\n      " + inline, html)
    index.write_text(html, encoding="utf-8")
    print("wrote web/index.html")


if __name__ == "__main__":
    main()
