package com.coparently.app.pushwording

/**
 * The names a push's data carries, as `shared-data`'s `PushPayload` declares them, repeated here
 * because this module depends on no other module of ours (the notification extension links it
 * alone). Only what the wording reads. `PushWordingGoldenTest` builds its payloads with
 * `PushPayload`'s constants, so a name that drifted here would word a push differently and fail it.
 */
internal object PushFields {
    /** The push's type; the iPhone's extension reads it (`wordPush`), Android's `PushNotifier` passes it apart. */
    const val TYPE = "type"

    /** The uid a push is addressed to; the iPhone's extension words nothing for another (`wordPush`). */
    const val TARGET_USER_ID = "targetUserId"

    const val ACTOR = "actorName"
    const val SUBJECT = "subject"
    const val DATE = "date"
    const val DAY_COUNT = "dayCount"
    const val DATES_TO_YOU = "datesToYou"
    const val DATES_TO_SENDER = "datesToSender"
    const val CHILD_NAMES = "childNames"
    const val ANSWER_BY = "answerBy"
    const val AMOUNT = "amount"
    const val SHARE = "share"
    const val CURRENCY = "currency"
    const val PREVIEW = "preview"
    const val RETAINED_UNTIL = "retainedUntilMillis"
    const val ACTIVITY_KIND = "activityKind"
    const val ACTIVITY_ENTITY_TYPE = "activityEntityType"
    const val ACTIVITY_ENTITY_ID = "activityEntityId"
    const val ACTIVITY_TITLE = "activityTitle"
    const val ACTIVITY_WHEN_ISO = "activityWhenIso"
    const val ACTIVITY_AMOUNT = "activityAmount"
    const val ACTIVITY_CURRENCY = "activityCurrency"

    const val CHAT_MESSAGE = "chat_message"
    const val DAY_SWAP_OFFERED = "day_swap_offered"
    const val DAY_SWAP_GROUP_OFFERED = "day_swap_group_offered"
}
