package com.coparently.app.presentation.onboarding

/**
 * The wizard's steps, in the order they are walked.
 *
 * **Four steps** (new-user review, October 2026, owner decision): the link, notifications, a name
 * and a country, and the custody schedule with a month preview, which is what a parent installs
 * the app for. The wizard used to be nine steps of form — the family question, every child, the
 * emergency contacts, the pets, the split — and the schedule came last, behind a button that left
 * the wizard. Those are asked for later now, from the Family tab, where their screens always were.
 *
 * **The co-parent link comes first, before a single question is asked** (September 2026, an
 * owner decision): it lets everything after it open on what the co-parent has already set up, so
 * the second parent checks rather than retypes.
 */
enum class OnboardingStep {
    /**
     * Hands off to `PairingScreen`, and shows what the link brought back once there is one.
     *
     * First, so that everything after it can open pre-filled. Skippable — "Not now" — because
     * the first parent to install the app has nobody to link with yet, and a link is never a
     * gate on somebody's calendar.
     */
    CoParent,

    /**
     * Asks for the system's permission to post notifications (Android 13 and later), right after
     * the link (tester feedback, 21 October 2026, owner decision: "push on by default").
     *
     * Every request, answer, reminder and chat message reaches the other phone as a notification,
     * and without this permission none of them appears. Skippable; on an older Android, or once
     * granted, it says notifications are on.
     */
    Notifications,

    /** The parent's name, colour and country. The only step with a required field. */
    Profile,

    /**
     * The custody schedule, chosen inside the wizard with the month it draws under it — or, when
     * the pair already shares one, that schedule to confirm. It never sends a proposal (see
     * `CustodyModelRepository.activateFirstSchedule`). Finishing here finishes onboarding.
     */
    Custody;

    /**
     * True when this step may be left without answering it.
     *
     * [Profile] is excluded because the parent's name is the one field the app genuinely cannot
     * work without: every event, expense and custody day is labelled with it. Everything else must
     * never become a gate on a parent's calendar.
     */
    val isSkippable: Boolean get() = this != Profile

    companion object {
        /** The steps the wizard walks, in order. */
        val steps: List<OnboardingStep> = entries.toList()
    }
}
