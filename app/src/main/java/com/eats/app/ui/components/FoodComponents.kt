package com.eats.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eats.app.data.Dish
import com.eats.app.data.Restaurant
import com.eats.app.data.money
import com.eats.app.order.FulfilmentMode
import com.eats.app.ui.theme.HeartRed
import com.eats.app.ui.theme.StarYellow
import com.eats.app.ui.theme.successColor

/** Delivery / Pick-up switch used on Home and the restaurant page. */
@Composable
fun ModeToggle(
    mode: FulfilmentMode,
    onChange: (FulfilmentMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FulfilmentMode.entries.forEach { m ->
                val selected = m == mode
                Surface(
                    onClick = { onChange(m) },
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) MaterialTheme.colorScheme.background else Color.Transparent,
                    shadowElevation = if (selected) 2.dp else 0.dp,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (m == FulfilmentMode.Delivery) "Delivery" else "Pick-up",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** "Great value" style tag. [positive] uses the green tint instead of the brand tint. */
@Composable
fun TagPill(text: String, modifier: Modifier = Modifier, positive: Boolean = false) {
    val container = if (positive) successColor().copy(alpha = 0.16f) else MaterialTheme.colorScheme.primaryContainer
    val content = if (positive) successColor() else MaterialTheme.colorScheme.tertiary
    Surface(modifier = modifier, shape = RoundedCornerShape(7.dp), color = container) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = content,
        )
    }
}

@Composable
fun CircleIconSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    color: Color = MaterialTheme.colorScheme.background,
    shadow: Dp = 3.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = CircleShape,
        color = color,
        shadowElevation = shadow,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
fun HeartButton(favourite: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    CircleIconSurface(onClick = onClick, modifier = modifier, size = 34.dp, shadow = 2.dp) {
        Icon(
            imageVector = if (favourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (favourite) "Remove from favourites" else "Add to favourites",
            tint = if (favourite) HeartRed else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * The restaurant cover. Uses the restaurant's photo when it has one, otherwise a tinted background
 * with a dotted pattern and a big tilted plate peeking in from the corner.
 */
@Composable
fun RestaurantCover(
    restaurant: Restaurant,
    modifier: Modifier = Modifier,
    plateFraction: Float = 0.62f,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val dotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceVariant),
                ),
            ),
    ) {
        if (restaurant.photo != null) {
            Image(
                painter = painterResource(restaurant.photo),
                contentDescription = restaurant.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Canvas(Modifier.matchParentSize()) {
                val step = 16.dp.toPx()
                var y = step / 2
                while (y < size.height) {
                    var x = step / 2
                    while (x < size.width) {
                        drawCircle(dotColor, 1.4.dp.toPx(), Offset(x, y))
                        x += step
                    }
                    y += step
                }
            }
            DishPlate(
                art = restaurant.art,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 24.dp, y = 34.dp)
                    .fillMaxWidth(plateFraction)
                    .rotate(-10f),
            )
        }
        content()
    }
}

/** A dish picture: the photo when there is one, otherwise the plate illustration. Always square. */
@Composable
fun DishImage(dish: Dish, modifier: Modifier = Modifier, steam: Boolean = false) {
    if (dish.photo != null) {
        Image(
            painter = painterResource(dish.photo),
            contentDescription = dish.name,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp)),
        )
    } else {
        DishPlate(dish.art, modifier, steam = steam)
    }
}

/** A restaurant in the Home feed. Use a fixed width for the horizontal "Popular" row. */
@Composable
fun RestaurantCard(
    restaurant: Restaurant,
    favourite: Boolean,
    onClick: () -> Unit,
    onFavourite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        RestaurantCover(restaurant, Modifier.aspectRatio(16f / 10f)) {
            if (restaurant.freeDeliveryOver > 0) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Text(
                        "R0 delivery over ${money(restaurant.freeDeliveryOver)}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            HeartButton(favourite, onFavourite, Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        Text(
            restaurant.name,
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        RestaurantMeta(restaurant)
        if (restaurant.tags.isNotEmpty()) {
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                restaurant.tags.forEachIndexed { i, tag -> TagPill(tag, positive = i > 0) }
            }
        }
    }
}

/** "★ 4.7 (1,200+) · R9 delivery · 18 min" */
@Composable
fun RestaurantMeta(restaurant: Restaurant, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = StarYellow, modifier = Modifier.size(14.dp))
        Text(
            text = " ${restaurant.rating}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        val fee = if (restaurant.deliveryFee == 0) "Free delivery" else "${money(restaurant.deliveryFee)} delivery"
        Text(
            text = " (${restaurant.reviews}) · $fee · ${restaurant.etaMinutes} min",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** − 2 + stepper. The minus sign is text because the core icon set has no minus. */
@Composable
fun QuantityStepper(
    qty: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 36.dp,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", "Remove one", onMinus, buttonSize)
            Text(
                "$qty",
                modifier = Modifier.padding(horizontal = 10.dp),
                style = MaterialTheme.typography.labelLarge,
            )
            StepButton("+", "Add one", onPlus, buttonSize)
        }
    }
}

@Composable
private fun StepButton(symbol: String, description: String, onClick: () -> Unit, size: Dp) {
    CircleIconSurface(onClick = onClick, size = size, shadow = 1.dp) {
        Text(
            symbol,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semanticsLabel(description),
        )
    }
}

private fun Modifier.semanticsLabel(label: String): Modifier = semantics { contentDescription = label }
