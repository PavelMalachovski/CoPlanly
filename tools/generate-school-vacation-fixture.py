#!/usr/bin/env python3
"""
Generates the reference fixture the MON-13 school-vacation tables are tested against.

School vacations outside Czechia are not computable: each German Land, Austria and Slovakia
publish them per school year. So the Kotlin tables in
`app/src/main/java/com/coparently/app/domain/holidays/` (`SlovakHolidays`, `AustrianHolidays`,
`GermanSchoolVacations`) are *data*, and what keeps them from being dates typed from memory is
this script and `SchoolVacationReferenceTest`, which compares every period and both names the
providers return with the fixture written here.

## Source

The OpenHolidays project's data repository, https://github.com/openpotato/openholidaysapi.data
(ODbL 1.0 - see "Licence" below), which is the source the OpenHolidays API
(https://openholidaysapi.org) serves. It is read at a **pinned commit** so the fixture is
reproducible and a regeneration is a deliberate diff. Before the tables were first written, a
sample of its dates was checked against the official publishers:

  * Germany - the KMK Ferienkalender (kmk.org, `FER2025_26.pdf`) and km.bayern.de for Bavaria's
    2025/26 dates (Herbstferien 3-7 Nov 2025, Buss- und Bettag 19 Nov 2025, Pfingstferien
    26 May-5 Jun 2026, Sommerferien 3 Aug-14 Sep 2026);
  * Austria - bmb.gv.at's "Schulferien in Oesterreich im Schuljahr 2025/2026" (Semesterferien
    2-7 Feb 2026 in Lower Austria and Vienna; Sommerferien 4 Jul-6 Sep 2026 in the east and
    11 Jul-13 Sep 2026 in the west);
  * Slovakia - minedu.sk's "Terminy prazdnin" for 2025/26 (autumn 30-31 Oct 2025, Christmas
    22 Dec 2025-7 Jan 2026, spring by region 16 Feb-6 Mar 2026, summer 1 Jul-31 Aug 2026) and
    2026/27 (autumn 29-30 Oct 2026, Christmas 23 Dec 2026-7 Jan 2027).

Re-run it by hand (it is not run by CI, which has no reason to depend on GitHub's raw host), and
read the diff: a changed line is either a ministry's new decision (update the table) or a
correction in the dataset (check it against the ministry, then update the table).

    python3 tools/generate-school-vacation-fixture.py \\
        > app/src/test/java/com/coparently/app/domain/holidays/SchoolVacationReferenceFixture.kt

`--source DIR` reads a local checkout of the repository instead of fetching; `--commit SHA`
overrides the pinned commit (then update `PINNED_COMMIT` below in the same change).

## What is drawn, and what is deliberately left out

Each choice is also stated in the provider it affects:

  * **From school year 2025/26 on** - every period starting on or after 1 September 2025 - and
    **up to whatever the dataset publishes**. Nothing is extrapolated.
  * **Germany, per Land only.** The school calendar is Land law; there is no nationwide period,
    so a German parent who has not named a Land sees none. Mecklenburg-Western Pomerania lists
    general schools (`MV-ABS`) and vocational schools (`MV-BBS`) separately; only general
    schools are drawn. Schleswig-Holstein's island exceptions (Sylt, Foehr, Amrum, Helgoland,
    the Halligen) are dropped - a Land setting cannot say a family lives on an island. The
    single school-free days each Land publishes (Buss- und Bettag in Bavaria, the day after
    Ascension in several Laender, ...) are kept: they are Land-wide and in the Land's own list.
  * **Austria, the nationwide periods only** (autumn, All Souls' Day, Christmas, Easter,
    Whitsun). The semester and summer breaks are set per Land, and the dataset marks every one
    after 2025/26 `Provisional` - and at least one provisional grouping disagrees with the
    ministry's published 2026/27 list. So the app offers no Austrian Land picker: it would add
    nothing but a past school year. The Laender's patron-saint days are school-free per Land
    and are also dropped; whether to draw them at all is the product decision ROADMAP MON-13
    records.
  * **Slovakia, the nationwide periods only.** The spring holidays are set per region (kraj),
    and the app has no Slovak region, so they are dropped - the same trade `CzechHolidays` makes
    for the district-dependent spring break. The one-day half-year holiday (polrocne prazdniny)
    the ministry also publishes is **not in the dataset** and so is not drawn.
  * **`Provisional` rows are dropped everywhere.**

The script exits rather than guessing when the data holds something these rules did not
anticipate: an unknown holiday name, a regional Slovak or Austrian row that is not a known
regional kind, or two periods overlapping in one table.

## Licence

The dataset is published under the Open Database License (ODbL) 1.0. The tables carry an
attribution comment, and the obligations for a published app build (attribution in the app's
notices) are recorded in ROADMAP MON-13.
"""

import argparse
import csv
import io
import os
import sys
import urllib.request

PINNED_COMMIT = "a42b397470b265d3f588fe4d8ec59db04e1e9678"
REPOSITORY = "openpotato/openholidaysapi.data"
RAW_URL = "https://raw.githubusercontent.com/{repo}/{commit}/src/{path}"

FIRST_DAY = "2025-09-01"

GERMAN_STATES = (
    "BB", "BE", "BW", "BY", "HB", "HE", "HH", "MV", "NI", "NW", "RP", "SH", "SL", "SN", "ST", "TH",
)
SLOVAK_REGIONS = {"BC", "BL", "KI", "NI", "PV", "TA", "TC", "ZI"}

# Local name -> (Kotlin enum entry, English name). The English names are this project's, not the
# dataset's (which varies the casing and wording between Laender for the same break); the local
# name is the dataset's own. The Kotlin `SchoolBreak` enum must carry the same pairs.
NAMES = {
    "Herbstferien": ("HERBSTFERIEN", "Autumn vacation"),
    "Weihnachtsferien": ("WEIHNACHTSFERIEN", "Christmas vacation"),
    "Winterferien": ("WINTERFERIEN", "Winter vacation"),
    "Frühjahrsferien": ("FRUEHJAHRSFERIEN", "Spring vacation"),
    "Fastnachtsferien": ("FASTNACHTSFERIEN", "Carnival vacation"),
    "Halbjahresferien": ("HALBJAHRESFERIEN", "Mid-year vacation"),
    "Halbjahrespause": ("HALBJAHRESPAUSE", "Mid-year break"),
    "Osterferien": ("OSTERFERIEN", "Easter vacation"),
    "Pfingstferien": ("PFINGSTFERIEN", "Whitsun vacation"),
    "Sommerferien": ("SOMMERFERIEN", "Summer vacation"),
    "Allerseelen": ("ALLERSEELEN", "All Souls' Day"),
    "Buß- und Bettag": ("BUSS_UND_BETTAG", "Repentance and Prayer Day"),
    "Reformationsfest": ("REFORMATIONSFEST", "Reformation Day"),
    "Gründonnerstag": ("GRUENDONNERSTAG", "Maundy Thursday"),
    "Himmelfahrt": ("HIMMELFAHRT", "Ascension break"),
    "Tag nach Himmelfahrt": ("TAG_NACH_HIMMELFAHRT", "Day after Ascension"),
    "Tag vor dem 1. Mai": ("TAG_VOR_DEM_1_MAI", "Day before 1 May"),
    "Tag vor dem 3. Oktober": ("TAG_VOR_DEM_3_OKTOBER", "Day before 3 October"),
    "Tage nach dem 3. Oktober": ("TAGE_NACH_DEM_3_OKTOBER", "Days after 3 October"),
    "Brückentag": ("BRUECKENTAG", "Bridge day"),
    "Ferientag": ("FERIENTAG", "Day off school"),
    "Schulfrei": ("SCHULFREI", "Day off school"),
    "Schulfreier Tag": ("SCHULFREIER_TAG", "Day off school"),
    "Unterrichtsfreier Tag": ("UNTERRICHTSFREIER_TAG", "Day off school"),
    "Variabler Ferientag": ("VARIABLER_FERIENTAG", "Day off school"),
    "Zusätzlicher Ferientag": ("ZUSAETZLICHER_FERIENTAG", "Day off school"),
    "Jesenné prázdniny": ("JESENNE_PRAZDNINY", "Autumn vacation"),
    "Vianočné prázdniny": ("VIANOCNE_PRAZDNINY", "Christmas vacation"),
    "Veľkonočné prázdniny": ("VELKONOCNE_PRAZDNINY", "Easter vacation"),
    "Letné prázdniny": ("LETNE_PRAZDNINY", "Summer vacation"),
}

# Regional rows that are dropped on purpose, by local name; anything else regional is an error.
AUSTRIAN_REGIONAL_DROPPED = {
    "Semesterferien", "Sommerferien", "St. Florian", "St. Josef", "St. Leopold", "St. Martin",
    "St. Rupert",
}
SLOVAK_REGIONAL_DROPPED = {"Jarné prázdniny"}

MAX_LINE = 120  # detekt MaxLineLength, which analyses test sources too


def read_csv(args, path):
    if args.source:
        with open(os.path.join(args.source, "src", path), encoding="utf-8-sig") as handle:
            text = handle.read()
    else:
        url = RAW_URL.format(repo=REPOSITORY, commit=args.commit, path=path)
        with urllib.request.urlopen(url, timeout=60) as response:
            text = response.read().decode("utf-8-sig")
    return list(csv.DictReader(io.StringIO(text), delimiter=";"))


def local_name(row, language):
    for part in row["Name"].split(","):
        if part.startswith(language + " "):
            return part[len(language) + 1:]
    sys.exit(f"{row['Id']}: no {language} name in {row['Name']!r}")


def period(row, language):
    name = local_name(row, language)
    if name not in NAMES:
        sys.exit(f"{row['Id']}: unknown holiday name {name!r} - add it to NAMES and SchoolBreak")
    entry, english = NAMES[name]
    start = row["StartDate"]
    end = row["EndDate"] or start
    return start, end, english, name


def wanted(row):
    tags = row.get("Tags") or ""
    return row["StartDate"] >= FIRST_DAY and "Provisional" not in tags


def german(args, state):
    rows = []
    for row in read_csv(args, f"de/holidays/holidays.school.{state.lower()}.csv"):
        if not wanted(row) or "Exception" in (row.get("Tags") or ""):
            continue
        groups = [g for g in (row.get("Groups") or "").split(",") if g]
        if groups and f"{state}-ABS" not in groups:
            continue
        subdivisions = row.get("Subdivisions")
        if subdivisions is not None and subdivisions not in ("", state):
            sys.exit(f"{row['Id']}: DE-{state} row names subdivisions {subdivisions!r}")
        rows.append(period(row, "DE"))
    return rows


def austrian(args):
    rows = []
    for row in read_csv(args, "at/holidays/holidays.school.csv"):
        if not wanted(row):
            continue
        if row["RegionalScope"] == "National":
            rows.append(period(row, "DE"))
        elif local_name(row, "DE") not in AUSTRIAN_REGIONAL_DROPPED:
            sys.exit(f"{row['Id']}: unexpected regional Austrian row {row['Name']!r}")
    return rows


def slovak(args):
    rows = []
    for row in read_csv(args, "sk/holidays/holidays.school.csv"):
        if not wanted(row):
            continue
        regions = {r for r in (row.get("Subdivisions") or "").split(",") if r}
        if row["RegionalScope"] == "National" or regions == SLOVAK_REGIONS:
            rows.append(period(row, "SK"))
        elif local_name(row, "SK") not in SLOVAK_REGIONAL_DROPPED:
            sys.exit(f"{row['Id']}: unexpected regional Slovak row {row['Name']!r}")
    return rows


def checked(key, rows):
    rows = sorted(set(rows))
    for (s1, e1, *_), (s2, e2, *_) in zip(rows, rows[1:]):
        if s2 <= e1:
            sys.exit(f"{key}: {s1}..{e1} overlaps {s2}..{e2}")
    for start, end, *_ in rows:
        if end < start:
            sys.exit(f"{key}: {start}..{end} ends before it starts")
    return rows


def kotlin_string(value):
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", help="a local checkout of " + REPOSITORY)
    parser.add_argument("--commit", default=PINNED_COMMIT)
    args = parser.parse_args()

    tables = {"AT": checked("AT", austrian(args)), "SK": checked("SK", slovak(args))}
    for state in GERMAN_STATES:
        tables[f"DE-{state}"] = checked(f"DE-{state}", german(args, state))

    source = "local checkout" if args.source else f"commit {args.commit}"
    out = [
        "package com.coparently.app.domain.holidays",
        "",
        "// GENERATED by tools/generate-school-vacation-fixture.py - do not edit by hand; re-run it.",
        f"// Source: github.com/{REPOSITORY} ({source}), ODbL 1.0.",
        "// Periods starting on or after " + FIRST_DAY + "; the script says what is dropped and why.",
        "",
        "/** The dataset commit the fixture below was generated from. */",
        f"internal const val SCHOOL_VACATION_REFERENCE_COMMIT = {kotlin_string(args.commit)}",
        "",
        "/** The first day a period may start on to be in the fixture. */",
        f"internal const val SCHOOL_VACATION_REFERENCE_FIRST_DAY = {kotlin_string(FIRST_DAY)}",
        "",
        "/**",
        " * Expected school vacations per `COUNTRY` or `COUNTRY-REGION` key, as (first day, last day,",
        " * English name, local name), in date order.",
        " */",
        "internal val schoolVacationReference: Map<String, List<ReferenceVacation>> = mapOf(",
    ]
    keys = list(tables)
    for index, key in enumerate(keys):
        out.append(f"    {kotlin_string(key)} to listOf(")
        rows = tables[key]
        for row_index, row in enumerate(rows):
            comma = "," if row_index < len(rows) - 1 else ""
            args_list = ", ".join(kotlin_string(value) for value in row)
            out.append(f"        ReferenceVacation({args_list}){comma}")
        out.append(f"    ){',' if index < len(keys) - 1 else ''}")
    out.append(")")
    for line in out:
        if len(line) > MAX_LINE:
            sys.exit(f"line exceeds {MAX_LINE} characters: {line}")
    sys.stdout.write("\n".join(out) + "\n")


if __name__ == "__main__":
    main()
