package com.coparently.app.e2e

import java.util.Locale

/**
 * What the UI tour's demo family *wrote* — event titles, notes, the chat, the plan, expenses, a
 * document's name — in the language of the variant being photographed, because the screenshots
 * double as store-listing images and a Czech screen full of English records reads as a mock-up.
 *
 * The family's names are not here: Alice, Bob, Emma, Leo and Max are the same people in every
 * language ([UiTourSeed.ALICE] and its siblings). Where a sentence names one of them, it is worded
 * so the name can stay as it is. Every table has the same shape — the same number of events,
 * messages and expenses — so a layout compares across languages. Amounts and currencies are the
 * seed's, not the table's. A language without a table takes English ([forLanguage]).
 *
 * @property events The calendar's titles, in the order [UiTourSeed] creates them.
 * @property emma Emma's health, school and activity details.
 * @property leo Leo's activity and kindergarten.
 * @property pet Max's details.
 * @property expenses The five expense titles; the jacket is what the tour waits for.
 * @property plan Both parents' parenting-plan answers.
 * @property chat The thread between Alice and Bob.
 * @property changeRequestNote Bob's reason for asking to move the dentist.
 * @property layerName The seasonal layer's name (the autumn break with Bob).
 * @property document The vault document.
 */
data class UiTourContent(
    val events: Events,
    val emma: EmmaDetails,
    val leo: LeoDetails,
    val pet: PetDetails,
    val expenses: Expenses,
    val plan: Plan,
    val chat: Chat,
    val changeRequestNote: String,
    val layerName: String,
    val document: Document
) {

    /** Event titles; [schoolPickup] and [dentist] are what the tour taps and waits for. */
    data class Events(
        val schoolPickup: String,
        val dentist: String,
        val dentistNote: String,
        val swimming: String,
        val therapy: String,
        val grandmaBirthday: String,
        val schoolTrip: String,
        val vaccination: String,
        val pianoRecital: String,
        val parentsEvening: String,
        val parentTeacherMeeting: String,
        val footballTraining: String,
        val handover: String,
        val birthdayParty: String
    )

    /** Emma's record: two allergies, an inhaler, piano, the medical note, an intolerance, a grandmother. */
    data class EmmaDetails(
        val allergies: List<String>,
        val medication: String,
        val dose: String,
        val medicationWhen: String,
        val activity: String,
        val activitySchedule: String,
        val activityTeacher: String,
        val medicalNotes: String,
        val intolerance: String,
        val grandmother: String
    )

    /** Leo's record: football and the kindergarten. */
    data class LeoDetails(
        val activity: String,
        val activitySchedule: String,
        val activityPlace: String,
        val teacher: String,
        val grade: String
    )

    /** A month's expenses: two in euros (the trip and the skis), the rest in crowns. */
    data class Expenses(
        val jacket: String,
        val piano: String,
        val dentist: String,
        val trip: String,
        val skis: String
    )

    /** Max, the beagle. */
    data class PetDetails(val breed: String, val vaccination: String, val feedingNotes: String)

    /** The weekday answer both parents agreed to, and their two holiday answers, still apart. */
    data class Plan(val weekdayAnswer: String, val aliceHolidays: String, val bobHolidays: String)

    /**
     * Seven messages, `true` for Alice's, then Bob's message with the consent form attached.
     * [search] is a word the tour types into chat search, and it occurs in [lines].
     */
    data class Chat(
        val lines: List<Pair<Boolean, String>>,
        val attachmentCaption: String,
        val attachmentFileName: String,
        val search: String
    ) {
        /** The thread's last plain message — what the tour waits for before its screenshot. */
        val lastLine: String get() = lines.last().second
    }

    /** A vault document's title and file name. */
    data class Document(val title: String, val fileName: String)

    /** The tables, one per language the app ships in. */
    companion object {
        /** The content for a BCP 47 [language] tag — its primary language, else English. */
        fun forLanguage(language: String): UiTourContent =
            when (Locale.forLanguageTag(language).language) {
                "cs" -> CZECH
                "de" -> GERMAN
                "ru" -> RUSSIAN
                "uk" -> UKRAINIAN
                else -> ENGLISH
            }

        /** English, and the fallback for any language without a table. */
        val ENGLISH = UiTourContent(
            events = Events(
                schoolPickup = "School pickup",
                dentist = "Dentist — Leo",
                dentistNote = "Dr. Horáková, Vinohradská 12. Bring the insurance card.",
                swimming = "Swimming lesson",
                therapy = "Therapy session",
                grandmaBirthday = "Grandma Jana's birthday",
                schoolTrip = "School trip to Krkonoše",
                vaccination = "Vaccination — Leo",
                pianoRecital = "Piano recital",
                parentsEvening = "Parents' evening",
                parentTeacherMeeting = "Parent-teacher meeting",
                footballTraining = "Football training",
                handover = "Handover at school",
                birthdayParty = "Emma's birthday party"
            ),
            emma = EmmaDetails(
                allergies = listOf("Peanuts", "Penicillin"),
                medication = "Salbutamol inhaler",
                dose = "100 mcg",
                medicationWhen = "before sport",
                activity = "Piano",
                activitySchedule = "Tuesdays 16:00",
                activityTeacher = "Mrs Dvořáková",
                medicalNotes = "Mild asthma — inhaler in the blue backpack pocket.",
                intolerance = "Lactose",
                grandmother = "Grandmother"
            ),
            leo = LeoDetails(
                activity = "Football",
                activitySchedule = "Thursdays 17:00",
                activityPlace = "Sparta youth pitch",
                teacher = "Ms Petra",
                grade = "Kindergarten"
            ),
            pet = PetDetails(breed = "Beagle", vaccination = "Rabies", feedingNotes = "Twice a day, no chicken."),
            expenses = Expenses(
                jacket = "Winter jacket for Emma",
                piano = "Piano lessons — October",
                dentist = "Dentist co-payment",
                trip = "School trip deposit",
                skis = "Ski rental"
            ),
            plan = Plan(
                weekdayAnswer = "Alternate weeks, Monday to Monday. Handover at school on Monday morning, " +
                    "or at 5 pm at home in the holidays. The other parent has Wednesday afternoon.",
                aliceHolidays = "Summer split into two halves, alternating which half each year.",
                bobHolidays = "Summer: July with Bob, August with Alice, swapping every year."
            ),
            chat = Chat(
                lines = listOf(
                    true to "Hi Bob, Emma's school trip is on the 2nd — can you sign the form?",
                    false to "Sure, I'll bring it on Monday at handover.",
                    true to "Thanks. Also, Leo has the dentist tomorrow at 10.",
                    false to "Noted. Does he still need the inhaler for football?",
                    true to "No, that's Emma. Leo is fine.",
                    false to "Ah right, sorry! Pickup moved to 5 pm on Wednesday, ok?",
                    true to "Ok, see you there 👍"
                ),
                attachmentCaption = "Here's the signed consent form.",
                attachmentFileName = "Trip-consent-form.pdf",
                search = "pickup"
            ),
            changeRequestNote = "I have a work meeting that morning — could we move it by a day?",
            layerName = "Autumn break",
            document = Document(title = "Custody agreement 2025", fileName = "Custody-agreement.pdf")
        )

        private val CZECH = UiTourContent(
            events = Events(
                schoolPickup = "Vyzvednutí ze školy",
                dentist = "Zubař — Leo",
                dentistNote = "MUDr. Horáková, Vinohradská 12. Vzít kartičku pojišťovny.",
                swimming = "Plavání",
                therapy = "Terapie",
                grandmaBirthday = "Narozeniny babičky Jany",
                schoolTrip = "Školní výlet do Krkonoš",
                vaccination = "Očkování — Leo",
                pianoRecital = "Klavírní besídka",
                parentsEvening = "Třídní schůzky",
                parentTeacherMeeting = "Konzultace s třídní učitelkou",
                footballTraining = "Fotbalový trénink",
                handover = "Předávání u školy",
                birthdayParty = "Oslava Emminých narozenin"
            ),
            emma = EmmaDetails(
                allergies = listOf("Arašídy", "Penicilin"),
                medication = "Salbutamol, inhalátor",
                dose = "100 µg",
                medicationWhen = "před sportem",
                activity = "Klavír",
                activitySchedule = "Úterý 16:00",
                activityTeacher = "paní Dvořáková",
                medicalNotes = "Lehké astma — inhalátor je v přední kapse modrého batohu.",
                intolerance = "Laktóza",
                grandmother = "Babička"
            ),
            leo = LeoDetails(
                activity = "Fotbal",
                activitySchedule = "Čtvrtek 17:00",
                activityPlace = "Hřiště mládeže Sparty",
                teacher = "paní učitelka Petra",
                grade = "Školka"
            ),
            pet = PetDetails(breed = "Bígl", vaccination = "Vzteklina", feedingNotes = "Dvakrát denně, žádné kuře."),
            expenses = Expenses(
                jacket = "Zimní bunda pro Emmu",
                piano = "Klavír — říjen",
                dentist = "Doplatek u zubaře",
                trip = "Záloha na školní výlet",
                skis = "Půjčení lyží"
            ),
            plan = Plan(
                weekdayAnswer = "Střídavě po týdnu, od pondělí do pondělí. Předávání v pondělí ráno ve škole, " +
                    "o prázdninách v 17:00 doma. Druhý rodič má středeční odpoledne.",
                aliceHolidays = "Letní prázdniny rozdělit na dvě poloviny a každý rok se v nich vystřídat.",
                bobHolidays = "Léto: červenec u Boba, srpen u Alice, každý rok naopak."
            ),
            chat = Chat(
                lines = listOf(
                    true to "Ahoj Bobe, Emma jede 2. na školní výlet — můžeš prosím podepsat souhlas?",
                    false to "Jasně, přinesu ho v pondělí při předávání.",
                    true to "Díky. A Leo má zítra v 10 zubaře.",
                    false to "Zapsáno. Potřebuje ještě na fotbal inhalátor?",
                    true to "Ne, to je Emma. Leo je v pořádku.",
                    false to "Aha, pardon! Vyzvednutí ve středu se posouvá na 17:00, jo?",
                    true to "Dobře, uvidíme se tam 👍"
                ),
                attachmentCaption = "Posílám podepsaný souhlas.",
                attachmentFileName = "Souhlas-s-vyletem.pdf",
                search = "vyzvednutí"
            ),
            changeRequestNote = "Ten den mám dopoledne pracovní schůzku — šlo by to posunout o den?",
            layerName = "Podzimní prázdniny",
            document = Document(title = "Dohoda o péči o děti 2025", fileName = "Dohoda-o-peci.pdf")
        )

        private val GERMAN = UiTourContent(
            events = Events(
                schoolPickup = "Abholung von der Schule",
                dentist = "Zahnarzt — Leo",
                dentistNote = "Dr. Horáková, Vinohradská 12. Versichertenkarte mitnehmen.",
                swimming = "Schwimmkurs",
                therapy = "Therapiesitzung",
                grandmaBirthday = "Geburtstag von Oma Jana",
                schoolTrip = "Klassenfahrt ins Riesengebirge",
                vaccination = "Impfung — Leo",
                pianoRecital = "Klaviervorspiel",
                parentsEvening = "Elternabend",
                parentTeacherMeeting = "Elterngespräch mit der Lehrerin",
                footballTraining = "Fußballtraining",
                handover = "Übergabe an der Schule",
                birthdayParty = "Emmas Geburtstagsfeier"
            ),
            emma = EmmaDetails(
                allergies = listOf("Erdnüsse", "Penicillin"),
                medication = "Salbutamol-Spray",
                dose = "100 µg",
                medicationWhen = "vor dem Sport",
                activity = "Klavier",
                activitySchedule = "Dienstags 16:00",
                activityTeacher = "Frau Dvořáková",
                medicalNotes = "Leichtes Asthma — das Spray steckt in der Außentasche des blauen Rucksacks.",
                intolerance = "Laktose",
                grandmother = "Großmutter"
            ),
            leo = LeoDetails(
                activity = "Fußball",
                activitySchedule = "Donnerstags 17:00",
                activityPlace = "Jugendplatz von Sparta",
                teacher = "Frau Petra",
                grade = "Kindergarten"
            ),
            pet = PetDetails(
                breed = "Beagle",
                vaccination = "Tollwut",
                feedingNotes = "Zweimal täglich, kein Hähnchen."
            ),
            expenses = Expenses(
                jacket = "Winterjacke für Emma",
                piano = "Klavierunterricht — Oktober",
                dentist = "Zuzahlung Zahnarzt",
                trip = "Anzahlung Klassenfahrt",
                skis = "Skiverleih"
            ),
            plan = Plan(
                weekdayAnswer = "Wöchentlich im Wechsel, von Montag bis Montag. Übergabe montagmorgens an der " +
                    "Schule, in den Ferien um 17 Uhr zu Hause. Der andere Elternteil hat den Mittwochnachmittag.",
                aliceHolidays = "Die Sommerferien in zwei Hälften teilen und jedes Jahr die Hälften tauschen.",
                bobHolidays = "Sommer: Juli bei Bob, August bei Alice, jedes Jahr umgekehrt."
            ),
            chat = Chat(
                lines = listOf(
                    true to "Hallo Bob, Emmas Klassenfahrt ist am 2. — kannst du das Formular unterschreiben?",
                    false to "Klar, ich bringe es am Montag zur Übergabe mit.",
                    true to "Danke. Übrigens hat Leo morgen um 10 den Zahnarzttermin.",
                    false to "Notiert. Braucht er beim Fußball noch das Asthmaspray?",
                    true to "Nein, das ist Emma. Leo geht's gut.",
                    false to "Ach ja, sorry! Das Abholen am Mittwoch ist jetzt um 17 Uhr, okay?",
                    true to "Okay, bis dann 👍"
                ),
                attachmentCaption = "Hier ist die unterschriebene Einverständniserklärung.",
                attachmentFileName = "Einverstaendniserklaerung-Klassenfahrt.pdf",
                search = "Abholen"
            ),
            changeRequestNote = "Ich habe an dem Vormittag ein Meeting — können wir es um einen Tag verschieben?",
            layerName = "Herbstferien",
            document = Document(title = "Sorgerechtsvereinbarung 2025", fileName = "Sorgerechtsvereinbarung.pdf")
        )

        private val RUSSIAN = UiTourContent(
            events = Events(
                schoolPickup = "Забрать из школы",
                dentist = "Стоматолог — Leo",
                dentistNote = "MUDr. Horáková, Vinohradská 12. Не забыть страховую карточку.",
                swimming = "Бассейн",
                therapy = "Сеанс психотерапии",
                grandmaBirthday = "День рождения бабушки Яны",
                schoolTrip = "Школьная поездка в Крконоше",
                vaccination = "Прививка — Leo",
                pianoRecital = "Отчётный концерт по фортепиано",
                parentsEvening = "Родительское собрание",
                parentTeacherMeeting = "Встреча с классной руководительницей",
                footballTraining = "Тренировка по футболу",
                handover = "Передача детей у школы",
                birthdayParty = "День рождения — Emma"
            ),
            emma = EmmaDetails(
                allergies = listOf("Арахис", "Пенициллин"),
                medication = "Сальбутамол, ингалятор",
                dose = "100 мкг",
                medicationWhen = "перед спортом",
                activity = "Фортепиано",
                activitySchedule = "По вторникам 16:00",
                activityTeacher = "пани Дворжакова",
                medicalNotes = "Лёгкая астма — ингалятор в переднем кармане синего рюкзака.",
                intolerance = "Лактоза",
                grandmother = "Бабушка"
            ),
            leo = LeoDetails(
                activity = "Футбол",
                activitySchedule = "По четвергам 17:00",
                activityPlace = "Детское поле «Спарты»",
                teacher = "Воспитательница Петра",
                grade = "Детский сад"
            ),
            pet = PetDetails(breed = "Бигль", vaccination = "Бешенство", feedingNotes = "Два раза в день, без курицы."),
            expenses = Expenses(
                jacket = "Зимняя куртка — Emma",
                piano = "Уроки фортепиано — октябрь",
                dentist = "Доплата у стоматолога",
                trip = "Предоплата за школьную поездку",
                skis = "Прокат лыж"
            ),
            plan = Plan(
                weekdayAnswer = "Понедельно, с понедельника до понедельника. Передача в понедельник утром в школе, " +
                    "на каникулах — в 17:00 дома. У второго родителя — среда после обеда.",
                aliceHolidays = "Летние каникулы делим пополам и каждый год меняемся половинами.",
                bobHolidays = "Лето: июль — Bob, август — Alice, каждый год наоборот."
            ),
            chat = Chat(
                lines = listOf(
                    true to "Привет! Emma 2-го едет на школьную экскурсию — подпишешь согласие?",
                    false to "Конечно, отдам в понедельник, когда будем передавать детей.",
                    true to "Спасибо. И ещё: Leo завтра в 10 идёт к стоматологу.",
                    false to "Записал. Ему на футболе ещё нужен ингалятор?",
                    true to "Нет, ингалятор — это Emma. Leo он не нужен.",
                    false to "Точно, прости! В среду забираем в 17:00, хорошо?",
                    true to "Хорошо, увидимся там 👍"
                ),
                attachmentCaption = "Вот подписанное согласие.",
                attachmentFileName = "Согласие-на-поездку.pdf",
                search = "стоматолог"
            ),
            changeRequestNote = "У меня в то утро рабочая встреча — можно перенести на день позже?",
            layerName = "Осенние каникулы",
            document = Document(title = "Соглашение об опеке 2025", fileName = "Соглашение-об-опеке.pdf")
        )

        private val UKRAINIAN = UiTourContent(
            events = Events(
                schoolPickup = "Забрати зі школи",
                dentist = "Стоматолог — Leo",
                dentistNote = "MUDr. Horáková, Vinohradská 12. Не забути страхову картку.",
                swimming = "Басейн",
                therapy = "Сеанс психотерапії",
                grandmaBirthday = "День народження бабусі Яни",
                schoolTrip = "Шкільна поїздка в Крконоше",
                vaccination = "Щеплення — Leo",
                pianoRecital = "Звітний концерт із фортепіано",
                parentsEvening = "Батьківські збори",
                parentTeacherMeeting = "Зустріч із класною керівницею",
                footballTraining = "Тренування з футболу",
                handover = "Передача дітей біля школи",
                birthdayParty = "День народження — Emma"
            ),
            emma = EmmaDetails(
                allergies = listOf("Арахіс", "Пеніцилін"),
                medication = "Сальбутамол, інгалятор",
                dose = "100 мкг",
                medicationWhen = "перед спортом",
                activity = "Фортепіано",
                activitySchedule = "Щовівторка 16:00",
                activityTeacher = "пані Дворжакова",
                medicalNotes = "Легка астма — інгалятор у передній кишені синього рюкзака.",
                intolerance = "Лактоза",
                grandmother = "Бабуся"
            ),
            leo = LeoDetails(
                activity = "Футбол",
                activitySchedule = "Щочетверга 17:00",
                activityPlace = "Дитяче поле «Спарти»",
                teacher = "Вихователька Петра",
                grade = "Дитячий садок"
            ),
            pet = PetDetails(breed = "Бігль", vaccination = "Сказ", feedingNotes = "Двічі на день, без курятини."),
            expenses = Expenses(
                jacket = "Зимова куртка — Emma",
                piano = "Уроки фортепіано — жовтень",
                dentist = "Доплата в стоматолога",
                trip = "Передоплата за шкільну поїздку",
                skis = "Прокат лиж"
            ),
            plan = Plan(
                weekdayAnswer = "Потижнево, з понеділка до понеділка. Передача в понеділок зранку в школі, " +
                    "на канікулах — о 17:00 вдома. У другого з батьків — середа після обіду.",
                aliceHolidays = "Літні канікули ділимо навпіл і щороку міняємося половинами.",
                bobHolidays = "Літо: липень — Bob, серпень — Alice, щороку навпаки."
            ),
            chat = Chat(
                lines = listOf(
                    true to "Привіт! Emma 2-го їде на шкільну екскурсію — підпишеш згоду?",
                    false to "Звісно, віддам у понеділок, коли передаватимемо дітей.",
                    true to "Дякую. І ще: Leo завтра о 10 йде до стоматолога.",
                    false to "Записав. Йому на футболі ще потрібен інгалятор?",
                    true to "Ні, інгалятор — це Emma. Leo він не потрібен.",
                    false to "Точно, вибач! У середу забираємо о 17:00, добре?",
                    true to "Добре, побачимося там 👍"
                ),
                attachmentCaption = "Ось підписана згода.",
                attachmentFileName = "Згода-на-поїздку.pdf",
                search = "стоматолог"
            ),
            changeRequestNote = "У мене того ранку робоча зустріч — можна перенести на день пізніше?",
            layerName = "Осінні канікули",
            document = Document(title = "Угода про опіку 2025", fileName = "Угода-про-опіку.pdf")
        )
    }
}
