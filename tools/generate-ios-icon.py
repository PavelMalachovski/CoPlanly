#!/usr/bin/env python3
"""Writes the iPhone app's icon from the Android launcher icon (MON-24 phase 3).

The source is the adaptive icon: the white calendar of `app/src/main/res/mipmap/
ic_launcher_foreground.xml` on the brand colour `ic_launcher_background` names (`brand_primary` in
`app/src/main/res/values/colors.xml`). An Android launcher shows the middle 72 dp of the 108 dp
layers; the iPhone draws its own rounded corners over a full square, so the icon is that same
middle 72 dp, rendered at 1024 px with no transparency (the App Store refuses an alpha channel).

Rendering needs cairosvg and Pillow, in a virtual environment of their own:

    python3 -m venv /tmp/icon-venv && /tmp/icon-venv/bin/pip install cairosvg pillow
    /tmp/icon-venv/bin/python tools/generate-ios-icon.py

Change the icon on Android, then run this; never edit the PNG by hand.
"""
import io
import pathlib
import re

import cairosvg
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
FOREGROUND = ROOT / "app/src/main/res/mipmap/ic_launcher_foreground.xml"
COLORS = ROOT / "app/src/main/res/values/colors.xml"
OUT = ROOT / "iosApp/CoPlanly/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png"
SIZE = 1024


def brand_colour():
    """The brand colour the launcher background draws, as `#RRGGBB`."""
    text = COLORS.read_text(encoding="utf-8")
    match = re.search(r'<color name="brand_primary">(#[0-9A-Fa-f]{6})</color>', text)
    if not match:
        raise SystemExit("brand_primary not found in " + str(COLORS))
    return match.group(1)


def foreground_svg(background):
    """The foreground vector as SVG over [background], cropped to the launcher's visible 72 dp."""
    xml = FOREGROUND.read_text(encoding="utf-8")
    group = re.search(
        r'android:pivotX="([\d.]+)"\s+android:pivotY="([\d.]+)"\s+'
        r'android:scaleX="([\d.]+)"\s+android:scaleY="([\d.]+)"', xml)
    if not group:
        raise SystemExit("the foreground's scale group was not found in " + str(FOREGROUND))
    px, py, sx, sy = group.groups()
    paths = []
    for attrs in re.findall(r"<path([^>]*)/>", xml):
        data = re.search(r'android:pathData="([^"]*)"', attrs).group(1)
        fill = re.search(r'android:fillColor="([^"]*)"', attrs).group(1)
        rule = ' fill-rule="evenodd"' if 'fillType="evenOdd"' in attrs else ""
        paths.append(f'<path fill="{fill}"{rule} d="{data}"/>')
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{SIZE}" height="{SIZE}" viewBox="18 18 72 72">'
        f'<rect x="0" y="0" width="108" height="108" fill="{background}"/>'
        f'<g transform="translate({px} {py}) scale({sx} {sy}) translate(-{px} -{py})">'
        + "".join(paths)
        + "</g></svg>"
    )


def main():
    background = brand_colour()
    png = cairosvg.svg2png(bytestring=foreground_svg(background).encode("utf-8"),
                           output_width=SIZE, output_height=SIZE)
    layer = Image.open(io.BytesIO(png)).convert("RGBA")
    rgb = tuple(int(background[i:i + 2], 16) for i in (1, 3, 5))
    flat = Image.new("RGBA", layer.size, rgb + (255,))
    flat.alpha_composite(layer)
    flat.convert("RGB").save(OUT, optimize=True)
    print("wrote", OUT.relative_to(ROOT))


if __name__ == "__main__":
    main()
