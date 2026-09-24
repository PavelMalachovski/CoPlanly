package com.coparently.app.presentation.childinfo.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coparently.app.R
import com.coparently.app.presentation.common.animations.sectionEnter
import com.coparently.app.presentation.common.animations.sectionExit

/**
 * Editor for managing a list of allergies.
 * Displays allergies as chips with ability to add and remove.
 *
 * @param allergies Current list of allergies
 * @param onAdd Callback when allergy is added
 * @param onRemove Callback when allergy is removed
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AllergyEditor(
    allergies: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isAddingNew by remember { mutableStateOf(false) }
    var newAllergy by remember { mutableStateOf("") }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Display existing allergies as chips
        if (allergies.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                allergies.forEachIndexed { index, allergy ->
                    // The whole chip removes it: it used to be a chip whose own tap did nothing, holding a
                    // 20dp remove button — under half the 48dp minimum. The chip enforces the minimum, and
                    // TalkBack hears "Remove <item>" rather than the item alone.
                    val removeLabel = stringResource(R.string.childinfo_remove)
                    AssistChip(
                        onClick = { onRemove(index) },
                        label = { Text(allergy) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.semantics { contentDescription = "$removeLabel $allergy" }
                    )
                }
            }
        }

        // Add new allergy form
        AnimatedVisibility(
            visible = isAddingNew,
            enter = sectionEnter(),
            exit = sectionExit()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newAllergy,
                    onValueChange = { newAllergy = it },
                    label = { Text(stringResource(R.string.childinfo_allergy_label)) },
                    placeholder = { Text(stringResource(R.string.childinfo_allergy_placeholder)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                Button(
                    onClick = {
                        if (newAllergy.isNotBlank()) {
                            onAdd(newAllergy)
                            newAllergy = ""
                            isAddingNew = false
                        }
                    },
                    enabled = newAllergy.isNotBlank()
                ) {
                    Text(stringResource(R.string.childinfo_add))
                }
            }
        }

        // Add button
        if (!isAddingNew) {
            OutlinedButton(
                onClick = { isAddingNew = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.childinfo_add))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.childinfo_add_allergy))
            }
        }
    }
}
