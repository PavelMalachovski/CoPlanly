# Chat message templates: audit, September 2026

Scope: the templates the chat composer's "Templates" chip opens (`domain/model/MessageTemplate.kt`,
`presentation/chat/MessageTemplatesBottomSheet.kt`, the `chat_template_*` keys in the five
`chat_strings.xml`). Read as a native speaker of each language and against the communication style
used for high-conflict co-parenting.

## The yardstick

- **BIFF**: brief, informative, friendly, firm. One topic per message.
- **About the children**, never about the other parent. No blame, no "sorry for the inconvenience"
  filler, no rhetorical questions.
- **A request carries a concrete proposal and a date to answer by.** "What do you think?" invites an
  essay; "Could you let me know by [date]?" invites a yes or no.
- **Some messages ask for nothing.** Sharing information with no demand attached is what keeps a
  thread calm, so the set has two of them (school update, acknowledgement).
- **Register**: every locale's templates address the co-parent informally (du / ty / ты / ти),
  as before. The app's own UI addresses the user formally in Czech, Russian and Ukrainian; that is
  the app speaking, and it does not apply to a message one parent writes to the other.
- **No gendered past tense in Czech, Russian or Ukrainian.** A Slavic past-tense verb ends
  differently for the sender's gender ("byl/byla", "был/была") and for the child's ("[Dítě] bylo"
  becomes "Emma byla"). The old bodies forced the parent to fix endings by hand. The new ones use
  present tense, "we" (plural past is genderless in Russian and Ukrainian), or a noun phrase.
- **No word from the tone hint's list** (`chat_nudge_words`, MON-19) and no "!!!". A template that
  triggered "Words like *never* can land harder than you mean" would contradict itself. Checked
  by script for all five locales: none do.
- **No app behaviour promised** (design refresh item 8). The expense template says "I can forward
  the receipt", not "the receipt is in Expenses", because a receipt photo is optional.

## Findings on the old set (8 templates)

**Across all locales**
- The category **"Conflict resolution"** held one template, a schedule swap. Filing an ordinary
  swap under "conflict" frames it as one. The category is gone.
- **"Holiday plan"** ended with "What do you think?": open-ended, no deadline.
- **"Schedule change"** opened with "I need to change the schedule", which states a demand before
  the ask. Now it is a swap offer with a reply-by date.
- **"Arriving earlier"** announced a change ("I will arrive earlier … Does that work for you?").
  Now it asks first and names the fallback: "If not, the usual time is fine."
- **"Running late"** asked for "[number of minutes]" but gave no arrival time, and apologised "for
  the inconvenience" (formal filler). Now: minutes late, a latest arrival time, "sorry for the
  wait".
- **"Child is unwell"** ended "I am staying at home with them", with no follow-up. Now it promises
  an update by a time, which is what the other parent needs.
- **"Doctor's visit"** had no medicine or dose line, the one thing the other parent must have for
  their days, and no next appointment. Now it is a labelled summary: reason, diagnosis and
  treatment, medicine (name, dose, until when), next check-up.
- **"Parent-teacher meeting"** informed but left the next step open. Now it asks whether the other
  parent is going and offers a summary if not.
- The sheet cut the two-line preview **without an ellipsis**, and category headings had no
  **heading semantics** for TalkBack. Both fixed in `MessageTemplatesBottomSheet.kt`.

**English**
- Category labels were in Title Case with "&" ("Pickup & Drop-off", "Illness & Medical"), against
  the sentence-case rule (D-21). Now "Handovers and schedule", "Health", "School", "Holidays and
  travel", "Expenses", "Replies".
- "I am running late", "I will arrive": stiff in a text message. Contractions now.

**Czech**
- "[Dítě] bylo dnes u lékaře", "[Dítě] se necítí dobře … Zůstávám s dítětem doma": the neuter
  hint produced wrong agreement for any real name, and "s dítětem" is odd about one's own child.
  Replaced by "[Dítě] má dnes …", "Posílám shrnutí dnešní návštěvy u lékaře s [dítětem]".
- "Mohli bychom si termíny vyměnit? Nabízím [náhradní datum]": masculine plural conditional; now
  "Šlo by si vyměnit dny? [datum] by připadl tobě a [jiné datum] mně."
- "Budeš se moci zúčastnit?" is bookish for a chat; now "Chystám se tam. Půjdeš taky?"
- "Třídní schůzka … Učitel chce …": Czech says "třídní schůzky" (plural), and "Učitel" assumed a
  male teacher; now "škola chce probrat [téma]".
- Category "Svátky a prázdniny" read as public holidays; the templates are about school holidays
  and trips: "Prázdniny a cestování".
- No-break spaces after single-letter prepositions and conjunctions (v, s, k, o, u, a) kept, as the
  i18n pass set them.

**German**
- "Entschuldige die Umstände" is formal filler; now "Entschuldige die Wartezeit".
- "Ich würde [Ersatztermin] übernehmen" was ambiguous about who takes which day; now "Du übernimmst
  [Datum], ich dafür [anderes Datum]".
- "Feiertage & Ferien" → "Ferien und Reisen"; "Abholen & Bringen" → "Übergaben und
  Betreuungsplan" (the app's own term, used 22 times elsewhere).
- "Empfehlung: [Empfehlung]" repeated the label as its hint; the hints now say what to write.

**Russian**
- "Я задержусь с передачей [ребёнка]" is understandable but unidiomatic; now "Опаздываю на
  передачу примерно на [сколько] минут, буду не позже [время]".
- "[Ребёнок] сегодня был у врача": masculine past for any child; now "Сегодня мы с [ребёнком] были
  у врача" (plural, genderless). Hints are in the case the sentence needs ("с [ребёнком]",
  "у [ребёнка]", "обсудить [тему]"), so the parent types the name already inflected.
- "Решение разногласий" dropped with its category.

**Ukrainian**
- "Хвороба та здоровʼя" kept the correct modifier apostrophe (U+02BC); the new "Здоровʼя" keeps it.
- "[Дитина] сьогодні була в лікаря": feminine past (following "дитина") for any child; now "Сьогодні
  ми з [дитиною] були в лікаря".
- Date ranges use "з … до …" rather than the Russianism "з … по …".
- "Погодьмо план" was fine; the new body uses "Сплануймо [канікули]" with a reply-by date.

## The new set (15 templates, 6 categories)

| Category | id | English title | Status |
|---|---|---|---|
| Handovers and schedule | `handover_confirm` | Confirm a handover | new |
| | `pickup_delay` | Running late | rewritten |
| | `pickup_early` | Ask for an earlier handover | rewritten (asks, gives a fallback) |
| | `packing` | Things to pack | new |
| | `schedule_change` | Ask to swap days | rewritten (offer + reply-by) |
| Health | `child_sick` | Child is unwell | rewritten (update-by time) |
| | `doctor_visit` | After a doctor's visit | rewritten (medicine, next check-up) |
| School | `school_event` | School event | rewritten |
| | `parent_teacher_meeting` | Parent-teacher meeting | rewritten (offers a summary) |
| | `school_update` | School update | new, asks for nothing |
| Holidays and travel | `holiday_plan` | Plan the holidays | rewritten (reply-by date) |
| | `travel_abroad` | Travel abroad | new (dates, address, phone, consent by a date) |
| Expenses | `expense_share` | Share of an expense | new (amount, share, pay-by date) |
| Replies | `acknowledge` | Got your message | new, asks for nothing |
| | `confirm_agreement` | Confirm what we agreed | new (a written summary of an agreement) |

Removed categories: Pickup and drop-off, Illness and medical, School events, Conflict resolution
(replaced by the six above). The ids of surviving templates are unchanged; nothing stores them.

`MessageTemplatesTest` pins the structure: unique ids, one resource per title and body, each
category one contiguous run in enum order, 12 to 16 templates.

## Open points

- **Travel abroad** deliberately asks for agreement without citing a law or form: which consent a
  border or airline wants differs by country, and a template must not pretend to legal advice.
- The Czech "domluvili"-style masculine plural was avoided rather than solved; "we" in Czech past
  tense is still gendered, so the Czech bodies stay in the present tense.
- Nothing substitutes the bracketed hints (see the KDoc on `MessageTemplate.placeholders`); the
  parent overwrites them by hand. A picker for dates and names would be a feature, not copy.
- The wording has been read by one reviewer per language here; a native speaker's pass on the
  device, in context, is still worth doing before release.
