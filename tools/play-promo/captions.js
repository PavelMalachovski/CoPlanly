'use strict';

/**
 * The store-listing slides: which UI tour screen each one shows, and what it says.
 *
 * Every claim here has to be true of the app as it ships (CLAUDE.md, design item 8): no AI, no
 * price, and the export is a record of what the parents wrote, not proof of what happened
 * (CLAUDE.md item 26). Headlines are sentence case (D-21) and short enough to read on a
 * thumbnail; the subline is optional.
 */
const SLIDES = [
  {
    shot: '11_calendar_month',
    cs: {
      headline: 'Jeden kalendář péče pro obě domácnosti',
      subline: 'Každý rodič má svou barvu, takže hned vidíte, čí je který den.',
    },
    en: {
      headline: 'One custody calendar for both homes',
      subline: 'Each parent has a colour, so you can see whose day it is at a glance.',
    },
  },
  {
    shot: '04_home_top',
    cs: {
      headline: 'Dnešek na první pohled',
      subline: 'U koho jsou děti a kdy je další předání.',
    },
    en: {
      headline: 'Today at a glance',
      subline: 'Who has the children and when the next handover is.',
    },
  },
  {
    shot: '01_home_dialog_swap',
    cs: {
      headline: 'Výměna dne, jen když souhlasíte oba',
      subline: 'Druhý rodič návrh přijme, nebo odmítne.',
    },
    en: {
      headline: 'Swap a day, only when you both agree',
      subline: 'The other parent accepts or declines the request.',
    },
  },
  {
    shot: '19_chat_thread',
    cs: {
      headline: 'Zprávy, které nikdo nepřepíše',
      subline: 'Odeslanou zprávu nelze upravit ani smazat.',
    },
    en: {
      headline: 'Messages nobody can rewrite',
      subline: 'A sent message can’t be edited or deleted.',
    },
  },
  {
    shot: '22_expenses_list',
    cs: {
      headline: 'Společné výdaje bez dohadování',
      subline: 'Kdo co zaplatil a kdo komu kolik dluží.',
    },
    en: {
      headline: 'Shared expenses, clearly split',
      subline: 'Who paid for what, and who owes whom.',
    },
  },
  {
    shot: '34_child_detail',
    cs: {
      headline: 'Vše o dětech na jednom místě',
      subline: 'Léky, alergie, kroužky i nouzové kontakty.',
    },
    en: {
      headline: 'Everything about the kids in one place',
      subline: 'Medication, allergies, activities and emergency contacts.',
    },
  },
  {
    shot: '50_export_scrolled_1',
    cs: {
      headline: 'Záznam pro advokáta nebo mediátora',
      subline: 'PDF nebo CSV s tím, co jste si zapsali a napsali.',
    },
    en: {
      headline: 'A record for your lawyer or mediator',
      subline: 'A PDF or CSV of what you both recorded and wrote.',
    },
  },
  {
    shot: '39_custody_setup',
    cs: {
      headline: 'Rozvrh péče podle vaší dohody',
      subline: 'Střídání po týdnu, 2-2-3, 3-4-4-3 i vlastní vzor.',
    },
    en: {
      headline: 'A schedule that fits your agreement',
      subline: 'Week on / week off, 2-2-3, 3-4-4-3 or a pattern of your own.',
    },
  },
];

/** The feature graphic's words. The phone on it shows the month grid alone, which has no text. */
const FEATURE = {
  cs: {
    tagline: 'Sdílený kalendář pro rodiče, kteří spolu nežijí',
    points: 'Péče · Výdaje · Domluva',
  },
  en: {
    tagline: 'The shared calendar for parents who live apart',
    points: 'Custody · Expenses · Messages',
  },
};

/** The screen the feature graphic shows. */
const FEATURE_SHOT = '11_calendar_month';

module.exports = { SLIDES, FEATURE, FEATURE_SHOT };
