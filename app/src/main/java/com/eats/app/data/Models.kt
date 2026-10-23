package com.eats.app.data

import androidx.annotation.DrawableRes

/** Which illustration is drawn on the plate when a dish has no photo. See ui/components/DishPlate.kt. */
enum class DishArt { Burger, Pizza, Chicken, Fries, Wrap, Bread, Gatsby, Sushi, Koek, Tart, Drink, Braai }

data class OptionChoice(val label: String, val price: Int = 0)

/**
 * A group of choices on a dish, e.g. "Spice level" (pick one) or "Extras" (pick any).
 * For single-choice groups the first choice is selected by default.
 * [hideDefault] keeps the free first choice (e.g. "Item only") out of the cart summary.
 */
data class OptionGroup(
    val id: String,
    val title: String,
    val multi: Boolean,
    val choices: List<OptionChoice>,
    val hideDefault: Boolean = false,
)

/**
 * One item on a menu. Shows [photo] when there is one, otherwise the [art] plate illustration.
 */
data class Dish(
    val id: String,
    val name: String,
    val description: String,
    val price: Int,
    val art: DishArt,
    val options: List<OptionGroup> = emptyList(),
    val tag: String? = null,
    @DrawableRes val photo: Int? = null,
)

data class MenuSection(val title: String, val items: List<Dish>)

data class Restaurant(
    val id: String,
    val name: String,
    val cuisine: String,
    val area: String,
    val art: DishArt,
    val categories: Set<String>,
    val rating: Double,
    val reviews: String,
    val deliveryFee: Int,
    val etaMinutes: Int,
    /** Delivery is free once the subtotal reaches this amount. 0 means no offer. */
    val freeDeliveryOver: Int,
    val tags: List<String>,
    val menu: List<MenuSection>,
    /** Cover photo. When null the cover is drawn from [art]. */
    @DrawableRes val photo: Int? = null,
) {
    val dishes: List<Dish> get() = menu.flatMap { it.items }
}

data class Category(val id: String, val label: String, val art: DishArt)

/** One row in the cart. [selections] maps an option group id to the chosen choice indexes. */
data class CartLine(
    val dish: Dish,
    val qty: Int,
    val selections: Map<String, Set<Int>>,
    val note: String,
    /** Price of one portion including paid options. */
    val unit: Int,
) {
    val total: Int get() = unit * qty

    /** e.g. "Large chips + large cooldrink, Extra cheese. Note: no onions" */
    fun summary(): String {
        val parts = mutableListOf<String>()
        dish.options.forEach { group ->
            selections[group.id].orEmpty().sorted().forEach { i ->
                val choice = group.choices[i]
                val isFreeDefault = !group.multi && i == 0 && choice.price == 0 && group.hideDefault
                if (!isFreeDefault) parts += choice.label
            }
        }
        if (note.isNotBlank()) parts += "Note: $note"
        return parts.joinToString(", ")
    }
}

/** A message in the support chat. */
data class ChatMessage(
    val id: Long,
    val text: String,
    val fromUser: Boolean,
)

fun money(amount: Int): String = "R$amount"
