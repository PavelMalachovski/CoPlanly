#!/usr/bin/env python3
"""
Generates the reference fixture the MON-13 holiday providers are tested against.

The Kotlin providers in `app/src/main/java/com/coparently/app/domain/holidays/` are pure,
computed code, written by hand. What makes them *verified* rather than remembered is that a
unit test (`HolidayReferenceTest`) compares every date and both names they produce, year by
year, with the output of an independently maintained dataset: the Python `holidays` library
(https://github.com/vacanza/holidays, MIT), which cites the legislation behind each rule.

This script writes that expected output as a Kotlin source file, so the test needs no
resource configuration and the fixture diffs readably in review. It is not run by CI — CI has
no reason to depend on PyPI. Re-run it by hand when bumping the library version or widening
the year range, and read the diff: a changed line is either a law that changed (update the
provider) or a library correction (check it, then update the provider).

    python3 -m venv /tmp/hv && /tmp/hv/bin/pip install holidays==0.105
    /tmp/hv/bin/python tools/generate-holiday-fixture.py \
        > app/src/test/java/com/coparently/app/domain/holidays/HolidayReferenceFixture.kt
    /tmp/hv/bin/python tools/generate-holiday-fixture.py --regions \
        > app/src/test/java/com/coparently/app/domain/holidays/HolidayRegionReferenceFixture.kt

`--regions` writes the second, regional fixture (MON-13's regional half): for every German Land,
the days that state adds **on top of** the nationwide list - not the whole list again, which would
be sixteen copies of the same nine days and a fixture six times the size. The script refuses to
write it if any state's list is not a superset of the nationwide one, since the test rebuilds each
state's list as "nationwide + these" and would otherwise pass over a missing day.

Regional choices, each deliberate and each also stated in `GermanState.kt`:

  * **Only the `public` category.** The library's `catholic` category (Assumption Day in Bavaria,
    Corpus Christi in parts of Saxony and Thuringia) holds only in Catholic-majority
    municipalities, which a state setting cannot identify.
  * **`Augsburg` is skipped.** The library models the city's Peace Festival as a pseudo-subdivision;
    it is a municipal holiday, not a Land.
  * **Austria has no regional fixture.** The library returns no regional *public* holiday for any
    of its nine Laender - the patron-saint days are in the `bank` category - so the app offers no
    Austrian region picker and there is nothing to pin.

Two deliberate differences from the library's output, both filtered here rather than hidden in
the test:

  * **Russia's moved days off are dropped** — both kinds the library carries: the transferred
    days ("Выходной (перенесено с …)") and the in-lieu days ("… (выходной)"). Both come from the
    government decree published each autumn for the following year, and the library lists them
    per year from those decrees rather than computing them: it has none after 2025. Art. 112's
    "a holiday on a weekend moves to the next working day" is not a substitute, because the
    decree routinely sends that day somewhere else (23 Feb 2025, a Sunday, became 8 May). So no
    computation can produce them for a future year, and the provider draws the statutory days
    of Labour Code art. 112 and nothing else.
  * **Ukraine is not generated at all.** Under martial law (since 24 Feb 2022) its public
    holidays are not days off, and the library returns none for 2023 onwards. The app keeps
    Ukraine without a provider and says why on the picker row.
"""

import sys

import holidays

COUNTRIES = ("SK", "DE", "AT", "RU")
YEARS = range(2020, 2036)

# Countries whose regional public holidays the app draws, and the library subdivisions it skips.
REGIONAL_COUNTRIES = ("DE",)
SKIPPED_SUBDIVISIONS = {"DE": ("Augsburg",)}

# Russia: the library labels its decree-based entries these two ways, and nothing else so.
RU_TRANSFER_PREFIX = "Выходной (перенесено"
RU_IN_LIEU_SUFFIX = " (выходной)"


def kotlin_string(value: str) -> str:
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'


def rows(country: str):
    local = holidays.country_holidays(country, years=YEARS)
    english = holidays.country_holidays(country, years=YEARS, language="en_US")
    for day in sorted(local):
        name_local = local[day]
        name_en = english[day]
        if "; " in name_local:
            # Two holidays on one date. None occurs in the range today; if a future version
            # produces one, the provider's one-entry-per-date model needs a decision first.
            sys.exit(f"{country} {day}: two holidays share a date: {name_local}")
        if country == "RU" and (
            name_local.startswith(RU_TRANSFER_PREFIX) or name_local.endswith(RU_IN_LIEU_SUFFIX)
        ):
            continue
        yield day, name_en, name_local


MAX_LINE = 120  # detekt MaxLineLength, which analyses test sources too


def regional_rows(country: str, subdiv: str):
    """The days `subdiv` adds to the nationwide list, as (date, English, local)."""
    national = holidays.country_holidays(country, years=YEARS)
    local = holidays.country_holidays(country, subdiv=subdiv, years=YEARS)
    english = holidays.country_holidays(country, subdiv=subdiv, years=YEARS, language="en_US")
    for day in national:
        if local.get(day) != national[day]:
            sys.exit(f"{country}-{subdiv} {day}: not a superset of the nationwide list")
    for day in sorted(local):
        if day in national:
            continue
        name_local = local[day]
        if "; " in name_local:
            sys.exit(f"{country}-{subdiv} {day}: two holidays share a date: {name_local}")
        yield day, english[day], name_local


def triple_lines(day, name_en, name_local, comma):
    """One fixture row, wrapped when it would pass MAX_LINE."""
    args = [kotlin_string(day.isoformat()), kotlin_string(name_en), kotlin_string(name_local)]
    line = f"        Triple({', '.join(args)}){comma}"
    if len(line) <= MAX_LINE:
        return [line]
    lines = ["        Triple("]
    for arg_index, arg in enumerate(args):
        arg_comma = "," if arg_index < len(args) - 1 else ""
        lines.append(f"            {arg}{arg_comma}")
    lines.append(f"        ){comma}")
    return lines


def main_regions():
    version = holidays.__version__
    out = [
        "package com.coparently.app.domain.holidays",
        "",
        "// GENERATED by tools/generate-holiday-fixture.py --regions - do not edit by hand; re-run it.",
        f"// Source: Python `holidays` library v{version}, "
        f"years {YEARS.start}-{YEARS.stop - 1}, category: public.",
        "// Each list is only what the region adds to the nationwide list; the script says why.",
        "",
        "/**",
        " * Expected regional public holidays per `COUNTRY-REGION` code, as (ISO date, English name,",
        " * local name): only the days the region adds to its country's list in [holidayReference].",
        " */",
        "internal val holidayRegionReference: Map<String, List<Triple<String, String, String>>> = mapOf(",
    ]
    keys = []
    for country in REGIONAL_COUNTRIES:
        cls = holidays.country_holidays(country).__class__
        for subdiv in cls.subdivisions:
            if subdiv in SKIPPED_SUBDIVISIONS.get(country, ()):
                continue
            keys.append((country, subdiv))
    for index, (country, subdiv) in enumerate(keys):
        out.append(f"    {kotlin_string(country + '-' + subdiv)} to listOf(")
        entries = list(regional_rows(country, subdiv))
        for row_index, (day, name_en, name_local) in enumerate(entries):
            comma = "," if row_index < len(entries) - 1 else ""
            out.extend(triple_lines(day, name_en, name_local, comma))
        comma = "," if index < len(keys) - 1 else ""
        out.append(f"    ){comma}")
    out.append(")")
    for line in out:
        if len(line) > MAX_LINE:
            sys.exit(f"line exceeds {MAX_LINE} characters: {line}")
    sys.stdout.write("\n".join(out) + "\n")


def main():
    if "--regions" in sys.argv[1:]:
        main_regions()
        return
    version = holidays.__version__
    out = [
        "package com.coparently.app.domain.holidays",
        "",
        "// GENERATED by tools/generate-holiday-fixture.py — do not edit by hand; re-run it.",
        f"// Source: Python `holidays` library v{version}, "
        f"years {YEARS.start}-{YEARS.stop - 1}, category: public.",
        "// Russia's decree-based moved days off are excluded; the script says why.",
        "",
        "/** The `holidays` library version the fixture below was generated from. */",
        f"internal const val HOLIDAY_REFERENCE_VERSION = {kotlin_string(version)}",
        "",
        "/** First year the fixture covers. */",
        f"internal const val HOLIDAY_REFERENCE_FIRST_YEAR = {YEARS.start}",
        "",
        "/** Last year the fixture covers. */",
        f"internal const val HOLIDAY_REFERENCE_LAST_YEAR = {YEARS.stop - 1}",
        "",
        "/** Expected public holidays per ISO country code, as (ISO date, English name, local name). */",
        "internal val holidayReference: Map<String, List<Triple<String, String, String>>> = mapOf(",
    ]
    for index, country in enumerate(COUNTRIES):
        out.append(f"    {kotlin_string(country)} to listOf(")
        entries = list(rows(country))
        for row_index, (day, name_en, name_local) in enumerate(entries):
            comma = "," if row_index < len(entries) - 1 else ""
            args = [kotlin_string(day.isoformat()), kotlin_string(name_en), kotlin_string(name_local)]
            line = f"        Triple({', '.join(args)}){comma}"
            if len(line) <= MAX_LINE:
                out.append(line)
            else:
                out.append("        Triple(")
                out.append(f"            {args[0]},")
                out.append(f"            {args[1]},")
                out.append(f"            {args[2]}")
                out.append(f"        ){comma}")
        comma = "," if index < len(COUNTRIES) - 1 else ""
        out.append(f"    ){comma}")
    out.append(")")
    for line in out:
        if len(line) > MAX_LINE:
            sys.exit(f"line exceeds {MAX_LINE} characters: {line}")
    sys.stdout.write("\n".join(out) + "\n")


if __name__ == "__main__":
    main()
