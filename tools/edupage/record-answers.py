#!/usr/bin/env python3
"""Record a real EduPage account's answers for CoPlanly's EduPage import (device check 3.36).

Run it on your own computer, never in a shared or cloud machine:

    python3 record-answers.py

It asks for the school's host, the username and the password (the password is read with
getpass: not echoed, not stored, not written anywhere). It then makes the requests the app's
EdupageClient makes (shared-data/.../data/school/edupage/EdupageClient.kt): the sign-in page,
the sign-in form, then for every child the child switch, four weeks of timetable and the
timeline from today. Python 3.8+, standard library only.

Two folders are written next to the script, under edupage-answers-<time>/:

  raw/       the answers exactly as EduPage sent them. They hold names, classes and the
             session cookie. Keep them; DO NOT send them to anybody. Delete them when done.
  redacted/  the same answers with every name, e-mail, phone number, address, free text and
             cookie replaced, and every id replaced by a stable pseudonym (the same id gets the
             same pseudonym everywhere, so the links between records survive). Look through
             these files before you send them; send only this folder.

Nothing is sent anywhere but to the EduPage hosts (*.edupage.org).
"""

import datetime
import getpass
import hashlib
import http.cookiejar
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

USER_AGENT = "okhttp/4.12.0"
TIMEOUT = 30

# Keys whose values are a person, a place or free text. Matched case-insensitively.
PERSONAL_KEYS = {
    "firstname", "lastname", "meno", "priezvisko", "user_meno", "name", "fullname", "short_name",
    "email", "e_mail", "mail", "phone", "telefon", "mobil", "mobile", "address", "adresa", "ulica",
    "mesto", "city", "psc", "zip", "street", "text", "nazov", "title", "popis", "description",
    "poznamka", "note", "body", "message", "sprava", "vlastnik_meno", "autor", "author",
    "schoolname", "school_name", "nazov_skoly", "username", "login", "user_name", "edupage",
    "ucitel", "teacher", "datnarodenia", "birthdate", "rodne_cislo", "parent1", "parent2",
    "parent3", "street_number", "gsechash", "csrftoken", "photo", "foto", "avatar", "url",
}
# Keys whose values are identifiers: kept as stable pseudonyms.
ID_KEY = re.compile(r"(^|_)(id|ids|userid|studentid|timelineid|ownerid|classid|classids|"
                    r"teacherid|teacherids|subjectid|classroomid|classroomids|groupid|groupids|"
                    r"vlastnik|user|cielove_osoby|reakcia_na|homeworkid|superid|pomocnik)$",
                    re.IGNORECASE)
# Keys left as they are: what the parsers branch on, dates and times.
KEPT_KEYS = {
    "typ", "type", "removed", "cas_udalosti", "cas_pridania", "cas_zmeny", "datefrom", "dateto",
    "date", "starttime", "endtime", "uniperiod", "period", "year", "dp", "showorig", "ttday",
    "changed", "cancelled", "removed", "is_removed", "duration", "isevent", "kind", "colors",
    "predmet_typ", "status", "stav", "allday", "datum", "datum_od", "datum_do", "datumod",
    "datumdo", "od", "do",
}

pseudonyms = {}


def pseudonym(value):
    """A stable stand-in for an id; a letter prefix ("Student", "Rodic") and a sign are kept."""
    text = str(value)
    match = re.fullmatch(r"([A-Za-z]*)(-?)(\d+)", text)
    if match is None:
        if text not in pseudonyms:
            pseudonyms[text] = "id%03d" % (len(pseudonyms) + 1)
        return pseudonyms[text]
    prefix, sign, digits = match.groups()
    if digits not in pseudonyms:
        pseudonyms[digits] = str(9000 + len(pseudonyms) + 1)
    return prefix + sign + pseudonyms[digits]


def redact_text(value):
    # Ids look like "Student1234", "Rodic56", "-12" or plain numbers.
    if re.fullmatch(r"[A-Za-z]*-?\d+", value) or re.fullmatch(r"[A-Za-z]+-?\d+(-\d+)*", value):
        return pseudonym(value)
    return value


def redact(node, key=None):
    lowered = (key or "").lower()
    if isinstance(node, dict):
        return {redact_key(k): redact(v, k) for k, v in node.items()}
    if isinstance(node, list):
        if ID_KEY.search(lowered):
            return [pseudonym(v) if isinstance(v, (str, int)) else redact(v) for v in node]
        return [redact(v, key) for v in node]
    if isinstance(node, str):
        nested = node.strip()
        if nested[:1] in "{[":
            try:
                return json.dumps(redact(json.loads(nested)), ensure_ascii=False)
            except ValueError:
                pass
        if lowered in KEPT_KEYS:
            return node
        if ID_KEY.search(lowered):
            return pseudonym(node)
        if lowered in PERSONAL_KEYS or lowered.startswith(("meno", "name", "nazov", "text")):
            return "REDACTED" if node else node
        if re.search(r"\d{1,2}:\d{2}|\d{4}-\d{2}-\d{2}", node) and len(node) <= 25:
            return node
        if len(node) > 3 and re.search(r"[^\W\d_]{3,}", node) and " " in node:
            return "REDACTED-TEXT"
        return redact_text(node)
    if isinstance(node, (int, float)) and ID_KEY.search(lowered) and not isinstance(node, bool):
        return pseudonym(node)
    return node


def redact_key(key):
    # dbi tables are keyed by id: {"students": {"1234": {...}}}
    if re.fullmatch(r"-?\d+", key) or re.fullmatch(r"[A-Za-z]+-?\d+", key):
        return pseudonym(key)
    return key


class EdupageOnly(urllib.request.HTTPRedirectHandler):
    """Follows a redirect only to an https host of the edupage.org family, as the app does."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        parts = urllib.parse.urlsplit(newurl)
        if parts.scheme != "https" or not is_edupage(parts.hostname or ""):
            raise urllib.error.HTTPError(newurl, code, "Redirect outside EduPage refused", headers, fp)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def is_edupage(host):
    return host == "edupage.org" or host.endswith(".edupage.org")


def make_opener():
    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar), EdupageOnly())
    opener.addheaders = [("User-Agent", USER_AGENT)]
    return opener, jar


def fetch(opener, url, data=None, content_type=None):
    body = None if data is None else data.encode("utf-8")
    req = urllib.request.Request(url, data=body)
    if content_type:
        req.add_header("Content-Type", content_type)
    try:
        with opener.open(req, timeout=TIMEOUT) as answer:
            return answer.status, answer.geturl(), answer.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as error:
        return error.code, error.geturl() or url, error.read().decode("utf-8", "replace")


def quoted_after(page, mark):
    start = page.find(mark)
    if start < 0:
        return None
    start += len(mark)
    end = page.find('"', start)
    return None if end < 0 else page[start:end]


def object_at(page, start):
    while start < len(page) and page[start].isspace():
        start += 1
    if start >= len(page) or page[start] != "{":
        return None
    depth, in_string, i = 0, False, start
    while i < len(page):
        c = page[i]
        if in_string and c == "\\":
            i += 1
        elif c == '"':
            in_string = not in_string
        elif not in_string and c == "{":
            depth += 1
        elif not in_string and c == "}":
            depth -= 1
            if depth == 0:
                return page[start:i + 1]
        i += 1
    return None


def userhome_of(page):
    call = page.find("userhome(")
    if call < 0:
        return None
    text = object_at(page, call + len("userhome("))
    if text is None:
        return None
    return json.loads(re.sub(r"[\t\r\n]", "", text))


class Recorder:
    def __init__(self, folder):
        self.raw = os.path.join(folder, "raw")
        self.redacted = os.path.join(folder, "redacted")
        os.makedirs(self.raw)
        os.makedirs(self.redacted)
        self.log = []

    def note(self, line):
        print(line)
        self.log.append(line)

    def save(self, name, text, is_json):
        with open(os.path.join(self.raw, name), "w", encoding="utf-8") as out:
            out.write(text)
        if is_json:
            try:
                clean = json.dumps(redact(json.loads(text)), ensure_ascii=False, indent=1)
            except ValueError:
                clean = "(not JSON: %d characters)" % len(text)
        else:
            clean = "(HTML page, %d characters, not copied)" % len(text)
        with open(os.path.join(self.redacted, name), "w", encoding="utf-8") as out:
            out.write(clean)

    def finish(self):
        with open(os.path.join(self.redacted, "log.txt"), "w", encoding="utf-8") as out:
            out.write("\n".join(self.log) + "\n")


def clean_url(url):
    parts = urllib.parse.urlsplit(url)
    query = urllib.parse.parse_qsl(parts.query, keep_blank_values=True)
    shown = "&".join("%s=%s" % (k, v if k in ("cmd", "bad", "cap", "lerr", "module", "akcia") else "…")
                     for k, v in query)
    host = parts.hostname or ""
    if host not in ("login1.edupage.org", "portal.edupage.org"):
        host = "SCHOOL.edupage.org"
    return "https://%s%s%s" % (host, parts.path, "?" + shown if shown else "")


def main():
    print(__doc__.split("\n\n")[0])
    host = input("School host [login1.edupage.org]: ").strip().lower() or "login1.edupage.org"
    if host.startswith("https://"):
        host = urllib.parse.urlsplit(host).hostname or ""
    if not is_edupage(host):
        sys.exit("Not an EduPage host: %s" % host)
    username = input("Username: ").strip()
    password = getpass.getpass("Password (not shown): ")

    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    folder = os.path.join(os.path.dirname(os.path.abspath(__file__)), "edupage-answers-" + stamp)
    rec = Recorder(folder)
    opener, jar = make_opener()
    origin = "https://" + host

    status, url, page = fetch(opener, origin + "/login/?cmd=MainLogin")
    rec.note("sign-in page: HTTP %s at %s" % (status, clean_url(url)))
    csrf = quoted_after(page, '"csrftoken":"')
    rec.note("csrf token found: %s" % bool(csrf))
    if not csrf:
        rec.save("01-login-page.html", page, False)
        rec.finish()
        sys.exit("No sign-in token on the page. Results in %s" % folder)

    form = urllib.parse.urlencode({"csrfauth": csrf, "username": username, "password": password})
    password = None
    status, url, page = fetch(opener, origin + "/login/edubarLogin.php", form,
                              "application/x-www-form-urlencoded")
    rec.note("sign-in form: HTTP %s, ended at %s" % (status, clean_url(url)))
    for mark, meaning in (("bad=1", "wrong username or password"), ("twofactor", "second factor"),
                          ("cap=1", "captcha"), ("lerr=b43b43", "captcha")):
        if mark in url:
            rec.note("EduPage refused the sign-in: %s (%s)" % (meaning, mark))
    userhome = userhome_of(page)
    rec.note("userhome( found: %s; ASC.gsechash found: %s"
             % (userhome is not None, quoted_after(page, 'ASC.gsechash="') is not None))
    if userhome is None:
        rec.save("02-after-sign-in.html", page, False)
        rec.finish()
        sys.exit("Not signed in. Results in %s" % folder)

    school_host = urllib.parse.urlsplit(url).hostname
    school = "https://" + school_host
    gsechash = quoted_after(page, 'ASC.gsechash="') or ""
    rec.save("02-userhome.json", json.dumps(userhome, ensure_ascii=False, indent=1), True)
    rec.note("top-level userhome keys: %s" % ", ".join(sorted(userhome.keys())))
    rec.note("cookie names: %s" % ", ".join(sorted({c.name for c in jar})))

    user_id = str(userhome.get("userid", ""))
    rec.note("userid kind: %s" % re.sub(r"\d", "", user_id))
    students = ((userhome.get("dbi") or {}).get("students")) or {}
    if user_id.startswith("Student"):
        child_ids = [user_id[len("Student"):]]
    else:
        child_ids = list(students.keys()) if isinstance(students, dict) else [
            str(s.get("id")) for s in students if isinstance(s, dict)]
    rec.note("children in dbi.students: %d" % len(child_ids))
    year = (userhome.get("dp") or {}).get("year")
    rec.note("dp.year: %s" % year)

    today = datetime.date.today()
    monday = today - datetime.timedelta(days=today.weekday())
    for index, child in enumerate(child_ids, start=1):
        tag = "child%d" % index
        status, url, answer = fetch(opener, school + "/login/switchchild?studentid=" +
                                    urllib.parse.quote(child, safe=""))
        rec.note("%s switch: HTTP %s, body %r" % (tag, status, answer.strip()[:20]))
        body = json.dumps({"__args": [None, {
            "year": year, "datefrom": str(monday), "dateto": str(monday + datetime.timedelta(days=27)),
            "table": "students", "id": child, "showColors": True, "showIgroupsInClasses": True,
            "showOrig": True, "log_module": "CurrentTTView"}], "__gsh": gsechash})
        status, url, answer = fetch(opener, school + "/timetable/server/currenttt.js?__func=curentttGetData",
                                    body, "application/json")
        rec.note("%s timetable: HTTP %s, %d characters" % (tag, status, len(answer)))
        rec.save("03-%s-timetable.json" % tag, answer, True)
        status, url, answer = fetch(opener, school + "/timeline/?module=todo&akcia=getData&filterTab=messages",
                                    urllib.parse.urlencode({"datefrom": str(today - datetime.timedelta(days=30))}),
                                    "application/x-www-form-urlencoded")
        rec.note("%s timeline: HTTP %s, %d characters" % (tag, status, len(answer)))
        rec.save("04-%s-timeline.json" % tag, answer, True)
        try:
            parsed = json.loads(answer)
            holder = parsed if "timelineItems" in parsed else parsed.get("r", {})
            types = {}
            for item in holder.get("timelineItems", []):
                types[item.get("typ")] = types.get(item.get("typ"), 0) + 1
            rec.note("%s timeline types: %s" % (tag, json.dumps(types, ensure_ascii=False)))
        except (ValueError, AttributeError):
            rec.note("%s timeline: not JSON" % tag)

    rec.finish()
    print("\nDone. Send only the folder %s" % rec.redacted)
    print("Keep or delete %s; it holds names and the session cookie." % rec.raw)


if __name__ == "__main__":
    main()
