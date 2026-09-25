package com.coparently.app.domain.model

import com.coparently.app.domain.consent.HealthConsent
import java.time.LocalDate

/**
 * Domain model representing a user (parent).
 * This is the clean architecture model used in the domain layer.
 *
 * There is deliberately no health data here. A parent's own allergies and medical profile were
 * removed (GDPR Art. 5(1)(c), data minimisation): `users/{uid}` is readable by the co-parent, so
 * an adult's diagnoses reached their ex-partner, and nothing in the product needed them. The Room
 * columns survive as dead ones (see `UserEntity.allergiesJson`) and `firestore.rules` refuses the
 * keys on `users/{uid}`. Children's medical profiles, on `ChildInfo`, are unaffected.
 *
 * @property id Unique identifier for the user
 * @property email Email address of the user
 * @property name Display name of the user
 * @property role Role of the user ("mom" or "dad")
 * @property colorCode Color code for displaying user's events in the calendar
 * @property profilePhotoUrl Optional URL for profile photo
 * @property googleCalendarSyncEnabled Whether Google Calendar sync is enabled
 * @property googleCalendarId Optional ID of the Google Calendar for sync
 * @property partnerId Optional ID of the co-parent partner (Firebase UID)
 * @property fcmToken Firebase Cloud Messaging token for push notifications
 * @property dateOfBirth The parent's own date of birth, or null until recorded
 * @property phone The parent's own phone number, free text, or null until recorded
 * @property onboardingCompletedAt ISO date-time at which this parent finished (or skipped
 * through) first-run onboarding; null while the wizard has not been completed
 * @property caresFor Whether this family is co-parenting children, pets, or both; empty until
 * the question is answered
 */
data class User(
    val id: String,
    val email: String,
    val name: String,
    val role: String, // "mom" or "dad"
    val colorCode: String, // Hex color code (e.g., "#FF4081" for pink, "#2196F3" for blue)
    val profilePhotoUrl: String? = null,
    val googleCalendarSyncEnabled: Boolean = false,
    val googleCalendarId: String? = null,
    val partnerId: String? = null,
    /**
     * Every co-parent this account has.
     *
     * [partnerId] is one of these — whichever family this device is currently showing — and
     * this is the whole set. See `UserEntity.partnerIdsJson`.
     */
    val partnerIds: List<String> = emptyList(),
    val fcmToken: String? = null,
    val dateOfBirth: LocalDate? = null,
    val phone: String? = null,
    /**
     * ISO date-time at which this parent finished (or skipped through) first-run onboarding.
     * Null means the wizard has not been completed. A string rather than a converted type
     * because that is how every date crosses this Firestore schema.
     */
    val onboardingCompletedAt: String? = null,
    /**
     * Whether this family is co-parenting children, pets, or both.
     *
     * Empty means the question has not been answered — every account that predates it — and is
     * read as "show everything" by [FamilyKind.effective], so an upgrade never hides a section
     * somebody was already using.
     */
    val caresFor: Set<FamilyKind> = emptySet(),
    /**
     * The country this parent lives in, as ISO 3166-1 alpha-2 (MON-13).
     *
     * Decides which public holidays and school vacations the calendar draws — see
     * [com.coparently.app.domain.holidays.HolidayCountry], which is the only thing that reads
     * it today. `"CZ"` for every account that predates the field, stamped by the v32→v33
     * migration rather than left null: the app is Czech-first, its one holiday table is the
     * Czech one, and a default makes an existing user's calendar identical to what they had.
     *
     * **A property of the person, not of the family.** Two separated parents can live in two
     * countries, and which public holidays apply is a fact about where *you* are. The cost is
     * that the school-vacation strips, which are genuinely about the child's school, follow the
     * viewer as well — the honest fix is a per-family school calendar, recorded under MON-13
     * rather than guessed at here.
     */
    val countryCode: String = "CZ",
    /**
     * The region within [countryCode] whose own public holidays the calendar adds (MON-13's
     * regional half), as an ISO 3166-2 suffix — a German Land such as `"BY"` — or null for the
     * nationwide calendar.
     *
     * Only meaningful when the country has regions to choose from
     * ([com.coparently.app.domain.holidays.HolidayCountry.regions], Germany alone today), and
     * read through `HolidayLocation.of`, which drops a code that does not belong to the country:
     * a parent who moves from Germany to Austria must not keep drawing Bavaria. A person's, not a
     * family's, for the reason [countryCode] is.
     */
    val regionCode: String? = null,
    /**
     * This parent's consent to entering their children's health details, or null when never
     * given or withdrawn — see [HealthConsent].
     *
     * Written only through `UserRepository.setHealthConsent`, never by the profile write
     * `UserRepository.updateUser` sends to Firestore, so no ordinary profile save can grant or
     * withdraw it. Read back from `users/{uid}.healthDataConsent` whenever the row is refreshed
     * from Firestore, so a second device knows.
     */
    val healthConsent: HealthConsent? = null
)
