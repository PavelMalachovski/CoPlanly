package com.coparently.app.presentation.common

import com.coparently.app.domain.model.BloodType
import com.coparently.app.resources.Res
import com.coparently.app.resources.medical_blood_type_a_negative
import com.coparently.app.resources.medical_blood_type_a_positive
import com.coparently.app.resources.medical_blood_type_ab_negative
import com.coparently.app.resources.medical_blood_type_ab_positive
import com.coparently.app.resources.medical_blood_type_b_negative
import com.coparently.app.resources.medical_blood_type_b_positive
import com.coparently.app.resources.medical_blood_type_o_negative
import com.coparently.app.resources.medical_blood_type_o_positive
import org.jetbrains.compose.resources.StringResource

/**
 * The string resource naming a blood group in the reader's language.
 *
 * Deliberately not a property on the enum and deliberately not `@Composable`: the enum stays free
 * of the UI, and returning the resource rather than a resolved string lets a call site inside a
 * conditional resolve it only when it has a value to show.
 *
 * @return the resource of the localized notation for this blood group
 */
fun BloodType.labelRes(): StringResource = when (this) {
    BloodType.A_POSITIVE -> Res.string.medical_blood_type_a_positive
    BloodType.A_NEGATIVE -> Res.string.medical_blood_type_a_negative
    BloodType.B_POSITIVE -> Res.string.medical_blood_type_b_positive
    BloodType.B_NEGATIVE -> Res.string.medical_blood_type_b_negative
    BloodType.AB_POSITIVE -> Res.string.medical_blood_type_ab_positive
    BloodType.AB_NEGATIVE -> Res.string.medical_blood_type_ab_negative
    BloodType.O_POSITIVE -> Res.string.medical_blood_type_o_positive
    BloodType.O_NEGATIVE -> Res.string.medical_blood_type_o_negative
}
