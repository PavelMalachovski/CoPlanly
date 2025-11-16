package com.coparently.app.presentation.calendar.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coparently.app.R
import com.coparently.app.presentation.theme.CoParentlyColors
import com.coparently.app.presentation.theme.dimensions

/**
 * Custody indicator for today's date.
 * Displays who has custody today with animated icon and color-coded styling.
 *
 * @param custody Custody owner: "mom", "dad", or other
 */
@Composable
fun CustodyIndicatorToday(custody: String) {
    val backgroundColor = when (custody) {
        "mom" -> CoParentlyColors.MomPink.copy(alpha = 0.2f)
        "dad" -> CoParentlyColors.DadBlue.copy(alpha = 0.2f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val borderColor = when (custody) {
        "mom" -> CoParentlyColors.MomPink
        "dad" -> CoParentlyColors.DadBlue
        else -> MaterialTheme.colorScheme.outline
    }

    val textColor = when (custody) {
        "mom" -> CoParentlyColors.MomPinkDark
        "dad" -> CoParentlyColors.DadBlueDark
        else -> MaterialTheme.colorScheme.onSurface
    }

    val text = when (custody) {
        "mom" -> stringResource(R.string.custody_with_mom)
        "dad" -> stringResource(R.string.custody_with_dad)
        else -> ""
    }

    // Animated icon rotation
    val animatedRotation by animateFloatAsState(
        targetValue = 360f,
        animationSpec = tween(
            durationMillis = 2000,
            easing = FastOutSlowInEasing
        ),
        label = "iconRotation"
    )

    val dims = dimensions()

    val custodyDescription = when (custody) {
        "mom" -> "Today's child is with Mom"
        "dad" -> "Today's child is with Dad"
        else -> "Custody information for today"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dims.paddingMedium, vertical = dims.paddingSmall)
            .semantics {
                contentDescription = custodyDescription
                role = Role.Image
            },
        shape = RoundedCornerShape(dims.cornerRadius),
        elevation = CardDefaults.cardElevation(
            defaultElevation = dims.cardElevation,
            pressedElevation = dims.cardElevation / 2,
            hoveredElevation = dims.cardElevation * 1.5f
        ),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        border = BorderStroke(2.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dims.paddingMedium),
            horizontalArrangement = Arrangement.spacedBy(dims.paddingSmall * 1.5f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Animated icon
            Icon(
                imageVector = when (custody) {
                    "mom" -> Icons.Default.Face
                    "dad" -> Icons.Default.Person
                    else -> Icons.Default.ChildCare
                },
                contentDescription = null,
                tint = borderColor,
                modifier = Modifier
                    .size(dims.iconSize * 1.33f)
                    .graphicsLayer {
                        rotationZ = animatedRotation
                    }
            )

            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = textColor
            )
        }
    }
}

