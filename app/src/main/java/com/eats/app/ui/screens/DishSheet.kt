package com.eats.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.eats.app.data.Dish
import com.eats.app.data.money
import com.eats.app.ui.components.DishImage
import com.eats.app.ui.components.QuantityStepper

/** Bottom sheet where the customer picks options, adds a note and chooses how many. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DishSheet(
    dish: Dish,
    onDismiss: () -> Unit,
    onAdd: (qty: Int, selections: Map<String, Set<Int>>, note: String, unit: Int) -> Unit,
) {
    var qty by remember { mutableIntStateOf(1) }
    var note by remember { mutableStateOf("") }
    var selections by remember {
        mutableStateOf<Map<String, Set<Int>>>(
            dish.options.associate { it.id to if (it.multi) emptySet() else setOf(0) },
        )
    }
    val unit = dish.price + dish.options.sumOf { g -> selections[g.id].orEmpty().sumOf { g.choices[it].price } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    DishImage(dish, Modifier.size(180.dp), steam = true)
                }
                Text(dish.name, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.headlineSmall)
                Text(
                    dish.description,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    money(dish.price),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )

                dish.options.forEach { group ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(group.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (group.multi) "OPTIONAL" else "CHOOSE 1",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    group.choices.forEachIndexed { i, choice ->
                        val chosen = i in selections[group.id].orEmpty()
                        val toggle = {
                            val current = selections[group.id].orEmpty()
                            val next = when {
                                !group.multi -> setOf(i)
                                chosen -> current - i
                                else -> current + i
                            }
                            selections = selections + (group.id to next)
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClick = toggle)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (group.multi) {
                                Checkbox(
                                    checked = chosen,
                                    onCheckedChange = { toggle() },
                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                                )
                            } else {
                                RadioButton(
                                    selected = chosen,
                                    onClick = toggle,
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                                )
                            }
                            Text(choice.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            if (choice.price > 0) {
                                Text(
                                    "+${money(choice.price)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                Text(
                    "Note for the kitchen",
                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(120) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Allergies, no onions, extra napkins…") },
                    shape = RoundedCornerShape(14.dp),
                    minLines = 2,
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                Box(Modifier.height(16.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                QuantityStepper(
                    qty = qty,
                    onMinus = { if (qty > 1) qty-- },
                    onPlus = { if (qty < 20) qty++ },
                )
                Button(
                    onClick = { onAdd(qty, selections, note.trim(), unit) },
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text("Add to cart", style = MaterialTheme.typography.labelLarge)
                    Box(Modifier.weight(1f))
                    Text(money(unit * qty), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
