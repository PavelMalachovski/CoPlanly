# CoPlanly — Personal data breach procedure (GDPR Art. 33–34)

> **Read this before you need it.** The 72-hour clock starts when the company becomes *aware* of
> a breach — when someone has a reasonable degree of certainty that one happened — not when the
> investigation ends. A company that learns this procedure during its first breach misses it.
>
> A **personal data breach** is any breach of security leading to the accidental or unlawful
> destruction, loss, alteration, unauthorised disclosure of, or access to, personal data (Art.
> 4(12)). It covers availability too: data lost with no backup is a breach, even if nobody read it.

## 1. Who does what

| Role | Who | Reach |
| --- | --- | --- |
| Incident lead | {{NAMED_PERSON}} (director of {{LEGAL_ENTITY_NAME}}) | {{PHONE}} |
| Technical lead | {{NAMED_PERSON}} (holds Firebase owner access) | {{PHONE}} |
| Counsel | {{ADVOKAT_NAME}} | {{PHONE}} |
| Supervisory authority | Úřad pro ochranu osobních údajů (ÚOOÚ), Pplk. Sochora 27, 170 00 Praha 7 | Online form: uoou.gov.cz → "Ohlášení porušení zabezpečení osobních údajů", or by data box (the ID is on uoou.gov.cz → Kontakty — check it when filling this table) |

## 2. The first hour: contain

1. **Write down the time** you learned of it and from whom. This is the start of the 72 hours.
2. **Stop the bleeding** without destroying evidence:
   - a wrong rule → `firebase deploy --only firestore:rules` / `storage` with the last known-good
     file from `main` (the rules tests prove it before it goes);
   - a leaked admin credential → revoke it in Google Cloud IAM, rotate service-account keys,
     review the audit log (Cloud Logging → Admin Activity);
   - a compromised user account → disable it in Firebase Authentication (do not delete: deletion
     tears down the evidence and the co-parent's shared data);
   - a leaked calendar-feed link → revoke the feed (`revokeCalendarFeed`) — the token's hash is
     the document id;
   - a leaked export → nothing to recall; note which record ID it was.
3. **Preserve evidence**: export the relevant Cloud Logging entries (they expire after 30 days) and
   a copy of the rules as they were.

## 3. Within 24 hours: assess

Answer, in the register (§6):

- **What data?** Especially: children's health data (`child_info.medicalProfile`,
  `medical_photos/{familyId}/…`), messages, documents in the vault, exports. Since L-4 a photo is
  readable only by the two uids its path names, so a leaked photo points at one family's parents
  or at a leaked credential, not at "any signed-in account".
- **Whose?** How many families; whether children are among the data subjects (almost always).
- **What happened to it?** Read, copied, altered, deleted, made unavailable.
- **By whom?** An outsider, another user, an ex-partner, Google, us.
- **Is it still happening?**

Then decide the risk to the people concerned — not to the company:

| Risk | Typical examples here | Notify ÚOOÚ? | Tell the families? |
| --- | --- | --- | --- |
| Unlikely | Data encrypted and the key safe; a rule opened for minutes with no read in the logs | No — record it (§6) | No |
| Risk | Account data (names, emails) exposed; calendar entries readable by another user | **Yes, within 72 h** | Usually no |
| High risk | **Any health data of a child**; messages; vault documents; data reached by an ex-partner | **Yes, within 72 h** | **Yes, without undue delay** (Art. 34) |

Health data about children is high risk by default. Assume notification to both unless counsel
concludes otherwise in writing.

## 4. Within 72 hours: notify the ÚOOÚ (Art. 33)

Use the ÚOOÚ's online form. It asks for, and Art. 33(3) requires:

1. the nature of the breach, the categories and approximate number of data subjects and records;
2. the contact point (the privacy contact);
3. the likely consequences;
4. the measures taken or proposed, including to mitigate harm.

If not everything is known, **notify what is known within 72 hours and send the rest in phases**
(Art. 33(4)). A late notification must say why it is late.

## 5. Tell the families (Art. 34)

When the risk is high: in plain language, in the app's languages, by email to the account address
and in the app. Say:

- what happened and when;
- what data of theirs and their children's was affected;
- what the likely consequences are;
- what we have done;
- what they can do (change the password, revoke calendar links, review who has access in
  Settings);
- the privacy contact.

Not needed if the data was unintelligible to the attacker (encrypted, key safe), if later measures
make the high risk unlikely, or if individual messages would take disproportionate effort — then a
public notice instead (Art. 34(3)).

## 6. The register (Art. 33(5)) — every breach, notified or not

Keep in a private document, retained 5 years:

| Date found | Date occurred | Description | Data and people affected | Consequences | Measures | ÚOOÚ notified (date, ref.) | Families told (date, how) | Why not notified (if not) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

## 7. Situations specific to CoPlanly

- **An ex-partner still reads after unpair.** A breach if the rules let them read what they should
  not. The chat is deliberately kept by both (it is not a breach that it remains readable).
- **A court order restricting a parent's access** to the child's information. Not a breach, but act
  on it: counsel reviews the order; the technical lead can remove a parent's access by ending the
  pairing (`unpairCoParent` as admin) — record it here even though it is not a breach.
- **A parent reports the other parent took over their account** (knew the password). A breach of
  that account's security: disable it, contact the parent through the account email, and treat
  the other parent's access to *their* side as unauthorised.
- **Google reports an incident** under the Data Processing Addendum. Google is our processor; its
  notice starts our 72 hours.
- **A lost or stolen phone** is not our breach — the device database is encrypted with a Keystore
  key — unless the report shows the protection failed.

## 8. After

Within two weeks: what failed, what changed (a rules test that would have caught it, a check in
CI), and whether the DPIA's risk register (`DPIA.md` §4) needs a new row or a higher rating.
