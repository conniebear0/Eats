package com.eats.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import com.eats.app.data.DishArt
import com.eats.app.ui.theme.plateFace

/**
 * A dish drawn on the Eats plate. The rim is the brand colour (orange in light mode, purple in dark),
 * the food is drawn in natural colours. All coordinates below are on a 100 x 100 grid.
 * Replace with real photos later by swapping this composable for an image loader.
 */
@Composable
fun DishPlate(
    art: DishArt,
    modifier: Modifier = Modifier,
    steam: Boolean = false,
    steamAlpha: Float = 1f,
) {
    val rim = MaterialTheme.colorScheme.primary
    val face = plateFace()
    val ink = MaterialTheme.colorScheme.onBackground
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val unit = size.minDimension / 100f
        scale(unit, unit, pivot = Offset.Zero) {
            val c = Offset(50f, 56f)
            drawCircle(rim, 43f, c)
            drawCircle(face, 34f, c)
            drawCircle(ink, 34f, c, style = Stroke(2.4f))
            if (steam) {
                val s = face.copy(alpha = steamAlpha)
                path("M40 10 c-3.5 -3 3.5 -5 0 -9", s, 2.6f)
                path("M50 10 c-3.5 -3 3.5 -5 0 -9", s, 2.6f)
                path("M60 10 c-3.5 -3 3.5 -5 0 -9", s, 2.6f)
            }
            drawFood(art, ink)
        }
    }
}

// ---------- small drawing helpers (100 x 100 grid) ----------

private fun DrawScope.path(d: String, color: Color, stroke: Float? = null) {
    val p = PathParser().parsePathString(d).toPath()
    if (stroke == null) drawPath(p, color)
    else drawPath(p, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.rrect(color: Color, x: Float, y: Float, w: Float, h: Float, r: Float) =
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(r))

private fun DrawScope.oval(color: Color, cx: Float, cy: Float, rx: Float, ry: Float) =
    drawOval(color, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2))

private fun DrawScope.dot(color: Color, cx: Float, cy: Float, r: Float) = drawCircle(color, r, Offset(cx, cy))

private fun DrawScope.line(color: Color, x1: Float, y1: Float, x2: Float, y2: Float, w: Float) =
    drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = w, cap = StrokeCap.Round)

private val Pivot = Offset(50f, 56f)

private fun DrawScope.drawFood(art: DishArt, ink: Color) {
    when (art) {
        DishArt.Burger -> {
            rrect(Color(0xFFE2A04B), 31f, 67f, 38f, 7f, 3.5f)
            rrect(Color(0xFF5A3320), 29f, 60f, 42f, 7f, 3.5f)
            path("M30 57 H70 L66 64 L60 60 L54 65 L48 60 L42 64Z", Color(0xFFF6C443))
            path("M29 55 q3.5 -4 7 0 t7 0 t7 0 t7 0 t7 0 t7 0 v2 H29Z", Color(0xFF7BB848))
            rrect(Color(0xFFD8452E), 31f, 50f, 38f, 4f, 2f)
            path("M30 50 C30 40 38 34 50 34 C62 34 70 40 70 50Z", Color(0xFFE9A94D))
            val seed = Color(0xFFFFF3D6)
            oval(seed, 41f, 42f, 2.2f, 1.2f); oval(seed, 50f, 38.5f, 2.2f, 1.2f); oval(seed, 59f, 42f, 2.2f, 1.2f)
            oval(seed, 46f, 46f, 2.2f, 1.2f); oval(seed, 55f, 46f, 2.2f, 1.2f)
        }

        DishArt.Pizza -> {
            dot(Color(0xFFD99A3C), 50f, 55f, 30f)
            dot(Color(0xFFF6D37A), 50f, 55f, 26f)
            path("M50 29 V81 M27.5 42 L72.5 68 M27.5 68 L72.5 42", Color(0xFFD99A3C), 1.4f)
            val pep = Color(0xFFC23B2B)
            dot(pep, 40f, 46f, 5f); dot(pep, 60f, 44f, 5f); dot(pep, 53f, 60f, 5f)
            dot(pep, 38f, 63f, 4.5f); dot(pep, 65f, 62f, 4.5f)
            oval(Color(0xFF5E9B3A), 50f, 50f, 3f, 1.8f); oval(Color(0xFF5E9B3A), 46f, 70f, 3f, 1.8f)
        }

        DishArt.Chicken -> {
            rotate(-28f, Pivot) { drumstick(Color(0xFFC8672A), Color(0xFFE0833E)) }
            rotate(30f, Pivot) { translate(-6f, 14f) { drumstick(Color(0xFFB95A22), Color(0xFFD87A38)) } }
        }

        DishArt.Fries -> {
            val fry = Color(0xFFF5C842)
            rotate(-9f, Offset(37f, 56f)) { rrect(fry, 35f, 30f, 5f, 28f, 2f) }
            rotate(-4f, Offset(42f, 56f)) { rrect(fry, 40f, 27f, 5f, 30f, 2f) }
            rrect(fry, 45f, 29f, 5f, 28f, 2f)
            rotate(3f, Offset(52f, 56f)) { rrect(fry, 50f, 26f, 5f, 31f, 2f) }
            rotate(7f, Offset(57f, 56f)) { rrect(fry, 55f, 30f, 5f, 28f, 2f) }
            rotate(11f, Offset(62f, 56f)) { rrect(fry, 60f, 33f, 5f, 25f, 2f) }
            path("M33 52 H67 L63.5 78 H36.5Z", Color(0xFFE24A3B))
            line(Color.White.copy(alpha = 0.9f), 36f, 61f, 64f, 61f, 5f)
        }

        DishArt.Wrap -> rotate(-25f, Pivot) {
            rrect(Color(0xFFE8CFA0), 24f, 44f, 54f, 24f, 12f)
            dot(Color(0xFF7BB848), 29f, 52f, 6.5f)
            dot(Color(0xFFD8452E), 30f, 60f, 5.5f)
            dot(Color(0xFFF6C443), 26f, 56f, 4f)
            path("M48 44 H66 C72.6 44 78 49.4 78 56 C78 62.6 72.6 68 66 68 H48Z", Color(0xFFB9BDC9))
            line(Color.White.copy(alpha = 0.5f), 52f, 44f, 52f, 68f, 1.5f)
            line(Color.White.copy(alpha = 0.5f), 57f, 44f, 57f, 68f, 1.5f)
        }

        DishArt.Bread -> {
            rrect(Color(0xFFE3B36B), 27f, 44f, 46f, 32f, 10f)
            drawRoundRect(Color(0xFFC48A3F), Offset(27f, 44f), Size(46f, 32f), CornerRadius(10f), style = Stroke(2f))
            oval(Color(0xFFF4D9A4), 50f, 46f, 20f, 9f)
            oval(Color(0xFFB8541E), 50f, 46f, 17f, 7.5f)
            dot(Color(0xFFE8863A), 43f, 45f, 3f); dot(Color(0xFFF2A64B), 56f, 47f, 3.2f); dot(Color(0xFF7BB848), 50f, 44f, 2.2f)
        }

        DishArt.Gatsby -> rotate(-18f, Pivot) {
            rrect(Color(0xFFD9A25A), 20f, 57f, 60f, 12f, 6f)
            rrect(Color(0xFF7BB848), 19f, 51f, 62f, 6f, 3f)
            rrect(Color(0xFFD8452E), 21f, 47f, 58f, 4.5f, 2.2f)
            val chip = Color(0xFFF5C842)
            listOf(24f to 44f, 31f to 43f, 38f to 44f, 60f to 43f, 67f to 44f).forEach { (x, y) -> rrect(chip, x, y, 4f, 9f, 1.5f) }
            path("M20 45 C20 36 34 31 50 31 C66 31 80 36 80 45Z", Color(0xFFE9A94D))
            val seed = Color(0xFFFFF3D6)
            oval(seed, 38f, 39f, 2.2f, 1.2f); oval(seed, 50f, 36.5f, 2.2f, 1.2f); oval(seed, 62f, 39f, 2.2f, 1.2f)
        }

        DishArt.Sushi -> {
            listOf(
                Triple(38f, 48f, Color(0xFFF08A6A)),
                Triple(62f, 48f, Color(0xFF8DBB52)),
                Triple(50f, 66f, Color(0xFFE0503D)),
            ).forEach { (x, y, filling) ->
                dot(Color(0xFF1F2A24), x, y, 11f)
                dot(Color(0xFFFBF7EE), x, y, 8.6f)
                dot(filling, x, y, 4.2f)
            }
            line(Color(0xFF8A5A32), 32f, 79f, 74f, 75f, 2.2f)
            line(Color(0xFF8A5A32), 33f, 83f, 75f, 79f, 2.2f)
        }

        DishArt.Koek -> {
            listOf(46f, 64f).forEach { y ->
                val d = "M25 $y c7 -12 12 12 25 0 s18 12 25 0"
                path(d, Color(0xFFC9772B), 9f)
                path(d, Color(0xFFE7A04A), 4.5f)
            }
        }

        DishArt.Tart -> {
            path("M26 72 Q50 28 74 72Z", Color(0xFFD9A25A))
            path("M26 72 Q50 28 74 72Z", Color(0xFFD9A25A), 3f)
            path("M32 67 Q50 40 68 67Z", Color(0xFFF7E7B0))
            val cin = Color(0xFFB8741F)
            dot(cin, 46f, 58f, 1.4f); dot(cin, 54f, 61f, 1.4f); dot(cin, 50f, 52f, 1.4f)
            dot(cin, 43f, 64f, 1.4f); dot(cin, 58f, 65f, 1.4f)
        }

        DishArt.Drink -> {
            line(Color(0xFF2F7D52), 57f, 44f, 64f, 25f, 3.2f)
            path("M36 42 H64 L60.8 76 H39.2Z", Color.White.copy(alpha = 0.85f))
            path("M36 42 H64 L60.8 76 H39.2Z", ink, 2f)
            path("M37.4 54 H62.6 L60.6 76 H39.4Z", Color(0xFFC9722B))
            rrect(ink, 35f, 39f, 30f, 4.5f, 2f)
            rrect(Color.White.copy(alpha = 0.55f), 41f, 57f, 7f, 7f, 1.5f)
            rrect(Color.White.copy(alpha = 0.55f), 51f, 63f, 7f, 7f, 1.5f)
        }

        DishArt.Braai -> {
            val wors = Color(0xFF8A3B1F)
            val shine = Color(0xFFB5582D)
            path("M26 47 C35 36 54 36 66 49", wors, 9f)
            path("M26 47 C35 36 54 36 66 49", shine, 3f)
            path("M28 62 C38 71 56 71 69 60", wors, 9f)
            path("M28 62 C38 71 56 71 69 60", shine, 3f)
            oval(Color(0xFFF6EFE0), 50f, 55f, 10f, 6.5f)
            dot(Color(0xFFD8452E), 66f, 55f, 5.5f)
            dot(Color(0xFF7BB848), 64f, 53f, 1.8f)
        }
    }
}

private fun DrawScope.drumstick(body: Color, shine: Color) {
    oval(body, 42f, 50f, 18f, 12f)
    oval(shine.copy(alpha = 0.75f), 37f, 46f, 9f, 4.5f)
    val bone = Color(0xFFF4EBDD)
    rrect(bone, 56f, 47.5f, 20f, 5f, 2.5f)
    dot(bone, 77f, 46f, 3.6f)
    dot(bone, 77f, 54f, 3.6f)
}
