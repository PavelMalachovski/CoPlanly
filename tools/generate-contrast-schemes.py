#!/usr/bin/env python3
"""Generates the medium- and high-contrast colour schemes from the standard ones.

    python3 tools/generate-contrast-schemes.py > app/src/main/java/com/coparently/app/presentation/theme/ContrastSchemes.kt

Android 14 lets a person ask every app for more contrast (Settings > Accessibility > Colour and
motion > Contrast), and Material defines what that means for each colour role as a contrast
target at three levels (docs/AUDIT-2026-10-design.md D-25, F-16). This script reads the two
standard schemes out of Theme.kt and Color.kt and moves each *foreground* role — text, icons,
outlines, the accent colours — along its own lightness, keeping its hue and chroma (CIELAB LCh),
to the nearest value that meets its target against every surface and container it can sit on.

Backgrounds are left exactly as they are. That is a deliberate departure from Material's own
generated schemes, which also re-tone the containers (a filled mid-tone container with white text
in medium contrast): the app pairs some containers with foregrounds that are not their own
on-colour, and a re-toned container would make those pairs worse, not better. With only the
foregrounds moving, every pairing in the app can only gain contrast.

The targets are Material's contrast curves (material-color-utilities, spec 2021) at contrast
0.5 and 1.0. A target no colour can reach (21:1 against anything but white or black) means the
extreme: black on a light theme, white on a dark one. ContrastSchemesTest holds every target.

No dependencies: the WCAG 2.x luminance and the CIELAB conversions are written out below.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
THEME = ROOT / 'app/src/main/java/com/coparently/app/presentation/theme'

SURFACES = ['background', 'surface', 'surfaceVariant', 'surfaceDim', 'surfaceBright',
            'surfaceContainerLowest', 'surfaceContainerLow', 'surfaceContainer',
            'surfaceContainerHigh', 'surfaceContainerHighest']
CONTAINERS = ['primaryContainer', 'secondaryContainer', 'tertiaryContainer', 'errorContainer']

# (role, backgrounds, (medium target, high target)), in dependency order: a fill before its
# on-colour, because the on-colour is measured against the fill as it will be drawn.
RULES = [
    ('onSurface', SURFACES + CONTAINERS, (11, 21)),
    ('onBackground', SURFACES, (11, 21)),
    ('onSurfaceVariant', SURFACES + CONTAINERS, (7, 11)),
    ('outline', SURFACES, (4.5, 7)),
    ('outlineVariant', SURFACES, (3, 4.5)),
    # Material stops the accents at 7:1 even at high contrast: past it, on a dark theme, primary,
    # tertiary and error would all reach white and stop meaning anything.
    ('primary', SURFACES, (7, 7)),
    ('secondary', SURFACES, (7, 7)),
    ('tertiary', SURFACES, (7, 7)),
    ('error', SURFACES, (7, 7)),
    ('onPrimary', ['primary'], (11, 21)),
    ('onSecondary', ['secondary'], (11, 21)),
    ('onTertiary', ['tertiary'], (11, 21)),
    ('onError', ['error'], (11, 21)),
    ('onPrimaryContainer', ['primaryContainer'], (7, 11)),
    ('onSecondaryContainer', ['secondaryContainer'], (7, 11)),
    ('onTertiaryContainer', ['tertiaryContainer'], (7, 11)),
    ('onErrorContainer', ['errorContainer'], (7, 11)),
    ('inverseOnSurface', ['inverseSurface'], (11, 21)),
    ('inversePrimary', ['inverseSurface'], (7, 7)),
]

# Rounding to 8-bit channels can land a hair under a target; aim a little above it.
MARGIN = 0.05


def read_tokens():
    """CoPlanlyColors' hex values by name, aliases resolved."""
    src = (THEME / 'Color.kt').read_text()
    tokens = {m.group(1): int(m.group(2), 16) & 0xFFFFFF
              for m in re.finditer(r'val (\w+) = Color\(0x([0-9A-Fa-f]{8})\)', src)}
    for m in re.finditer(r'val (\w+) = (\w+)\s*$', src, re.M):
        if m.group(2) in tokens:
            tokens[m.group(1)] = tokens[m.group(2)]
    return tokens


def read_scheme(name, tokens):
    src = (THEME / 'Theme.kt').read_text()
    body = re.search(r'val ' + name + r' = \w+ColorScheme\((.*?)\n\)', src, re.S).group(1)
    scheme = {}
    for line in body.split('\n'):
        m = re.match(r'\s*(\w+) = (.+?),?\s*$', line)
        if not m:
            continue
        role, value = m.group(1), m.group(2)
        if value.startswith('Color(0x'):
            scheme[role] = int(value[8:16], 16) & 0xFFFFFF
        elif value == 'Color.White':
            scheme[role] = 0xFFFFFF
        elif value == 'Color.Black':
            scheme[role] = 0x000000
        elif value.startswith('CoPlanlyColors.'):
            scheme[role] = tokens[value.split('.', 1)[1]]
        else:
            raise SystemExit(f'{name}.{role}: cannot read {value!r}')
    return scheme


def lin(c):
    c /= 255
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def luminance(rgb):
    r, g, b = (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255
    return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)


def contrast(a, b):
    la, lb = sorted((luminance(a), luminance(b)), reverse=True)
    return (la + 0.05) / (lb + 0.05)


def to_lab(rgb):
    r, g, b = (lin((rgb >> s) & 255) for s in (16, 8, 0))
    x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047
    y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
    z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883
    f = [t ** (1 / 3) if t > 216 / 24389 else (24389 / 27 * t + 16) / 116 for t in (x, y, z)]
    return 116 * f[1] - 16, 500 * (f[0] - f[1]), 200 * (f[1] - f[2])


def from_lab(L, a, b):
    """sRGB for a Lab colour, or None when it lies outside the gamut."""
    fy = (L + 16) / 116
    fx, fz = fy + a / 500, fy - b / 200
    inv = [t ** 3 if t ** 3 > 216 / 24389 else (116 * t - 16) / (24389 / 27) for t in (fx, fy, fz)]
    x, y, z = inv[0] * 0.95047, inv[1], inv[2] * 1.08883
    rl = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z
    gl = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z
    bl = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z
    out = 0
    for c in (rl, gl, bl):
        if c < -1e-4 or c > 1 + 1e-4:
            return None
        c = min(max(c, 0.0), 1.0)
        c = 12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055
        out = (out << 8) | round(c * 255)
    return out


def at_lightness(rgb, L):
    """The colour's hue at lightness L, with as much of its chroma as the gamut allows."""
    _, a, b = to_lab(rgb)
    for step in range(101):
        k = 1 - step / 100
        found = from_lab(L, a * k, b * k)
        if found is not None:
            return found
    raise ValueError('no colour at this lightness')


def solve(rgb, backgrounds, target):
    """The nearest colour to [rgb], along its lightness, clearing [target] on every background."""
    if all(contrast(rgb, bg) >= target for bg in backgrounds):
        return rgb
    L0 = to_lab(rgb)[0]
    darker = all(luminance(rgb) <= luminance(bg) for bg in backgrounds)
    lighter = all(luminance(rgb) >= luminance(bg) for bg in backgrounds)
    if darker == lighter:
        raise SystemExit(f'#{rgb:06X} sits between its backgrounds; no single direction clears them')
    step = -0.1 if darker else 0.1
    L = L0
    while 0 <= L + step <= 100:
        L += step
        candidate = at_lightness(rgb, L)
        if all(contrast(candidate, bg) >= target + MARGIN for bg in backgrounds):
            return candidate
    return 0x000000 if darker else 0xFFFFFF


def derive(standard, level):
    scheme = dict(standard)
    for role, backgrounds, targets in RULES:
        scheme[role] = solve(scheme[role], [scheme[b] for b in backgrounds], targets[level])
    return scheme


def kotlin(name, base, standard, scheme, doc):
    lines = ['/**', *[f' * {line}' for line in doc], ' */', f'internal val {name}: ColorScheme = {base}.copy(']
    changed = [r for r in scheme if scheme[r] != standard[r]]
    for i, role in enumerate(changed):
        comma = ',' if i < len(changed) - 1 else ''
        lines.append(f'    {role} = Color(0xFF{scheme[role]:06X}){comma}')
    lines.append(')')
    return '\n'.join(lines)


def main():
    tokens = read_tokens()
    light = read_scheme('LightColorScheme', tokens)
    dark = read_scheme('DarkColorScheme', tokens)
    out = [
        'package com.coparently.app.presentation.theme',
        '',
        'import androidx.compose.material3.ColorScheme',
        'import androidx.compose.ui.graphics.Color',
        '',
        '// Generated by tools/generate-contrast-schemes.py from the standard schemes in Theme.kt.',
        '// Do not edit by hand: change the standard scheme, or the script, and regenerate.',
        '// ContrastSchemesTest holds every target the script aims for.',
        '',
    ]
    for name, base, standard in (('Light', 'LightColorScheme', light), ('Dark', 'DarkColorScheme', dark)):
        for level, label in ((0, 'Medium'), (1, 'High')):
            scheme = derive(standard, level)
            doc = [f'The {name.lower()} scheme at {label.lower()} contrast: foregrounds moved to Material\'s',
                   f'{label.lower()}-contrast targets, backgrounds unchanged.']
            out.append(kotlin(f'{name}{label}ContrastColorScheme', base, standard, scheme, doc))
            out.append('')
    sys.stdout.write('\n'.join(out))


if __name__ == '__main__':
    main()
