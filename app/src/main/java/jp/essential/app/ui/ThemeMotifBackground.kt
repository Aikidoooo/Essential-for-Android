package jp.essential.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import jp.essential.app.AppIconManager
import jp.essential.app.R
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.min

internal data class MotifShape(val path: Path, val filled: Boolean = false, val strength: Float = 1f)

/** テーマの象徴を余白へ配置する。パスはサイズ変更時だけ生成し、常時アニメーションを行わない。 */
@Composable
internal fun ThemeMotifBackground(themeId: String, dark: Boolean, modifier: Modifier = Modifier, motionEnabled: Boolean = true) {
    val portraitAtlas = ImageBitmap.imageResource(R.drawable.theme_character_tinted_v2)
    val portraitIds = remember { listOf("mint", "sunset", "violet", "forest", "ocean", "ruby", "sky", "rose") }
    Crossfade(themeId, modifier.fillMaxSize(), animationSpec = spring(stiffness = 380f), label = "テーマ模様の切替") { id ->
        if (id == "reference") {
            RulerOfDeathBackground(dark, motionEnabled)
            return@Crossfade
        }
        val lunarCompanion = if (id == "sky") ImageBitmap.imageResource(R.drawable.lunar_companion_tinted_v1) else null
        val lunarFloralArtwork = if (id == "sky") ImageBitmap.imageResource(R.drawable.lunar_floral_renewal_v1) else null
        val geoPairArtwork = if (id == "sunset") ImageBitmap.imageResource(R.drawable.geo_back_to_back_v1) else null
        val electroArtwork = if (id == "violet") ImageBitmap.imageResource(R.drawable.electro_sword_renewal_v1) else null
        val featuredArtwork = geoPairArtwork ?: electroArtwork
        val dendroCompanion = if (id == "forest") ImageBitmap.imageResource(R.drawable.dendro_companion_tinted_v2) else null
        val flyingCompanion = lunarCompanion ?: dendroCompanion
        val anemoCircle = if (id == "mint") ImageBitmap.imageResource(R.drawable.anemo_magic_circle_v1) else null
        val pyroArtwork = if (id == "ruby") ImageBitmap.imageResource(R.drawable.pyro_motorcycle_tinted_v2) else null
        val hydroArtwork = if (id == "ocean") ImageBitmap.imageResource(R.drawable.hydro_water_tinted_v2) else null
        val splashArtwork = pyroArtwork ?: hydroArtwork
        val cryoAttendants = if (id == "rose") ImageBitmap.imageResource(R.drawable.cryo_attendants_tinted_v2) else null
        Box(Modifier.fillMaxSize().drawWithCache {
            val shapes = mutableListOf<MotifShape>()
            val isRose = id == "reference"
            val portraitIndex = portraitIds.indexOf(id)
            val anchors = if (isRose) listOf(Offset(size.width * .88f, size.height * .16f),
                Offset(size.width * .08f, size.height * .53f), Offset(size.width * .88f, size.height * .87f))
                else listOf(Offset(size.width * .5f, size.height * .32f))
            anchors.forEachIndexed { index, center ->
                val radius = size.width * if (!isRose) .36f else if (index == 1) .24f else .29f
                if (isRose) shapes += buildThemeMotif(id, center, radius)
            }
            val elementColor = Color(AppIconManager.optionForId(id).backgroundEnd)
            val tint = if (isRose) { if (dark) Color(0xFFDAEEFF) else Color(0xFF283D43) }
                else lerp(elementColor, if (dark) Color.White else Color.Black, if (dark) .65f else .35f)
            val opacity = if (isRose) { if (dark) .13f else .095f } else if (dark) .55f else .42f
            val stroke = Stroke(width = size.width * if (isRose) .0023f else .0035f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val cellSize = IntSize(portraitAtlas.width / 4, portraitAtlas.height / 2)
            // 原本を保ったまま、人物と無関係なセル境界の区切り線だけを表示から外す。
            val borderInset = 4
            val sourceSize = IntSize(cellSize.width - borderInset * 2, cellSize.height - borderInset * 2)
            // 縦横比を維持して全身を画面内へ収める。横長画面でも上下を切り取らない。
            val portraitScale = min(size.width * (if (cryoAttendants != null) .68f else .84f) / sourceSize.width, size.height * .72f / sourceSize.height)
            val portraitSize = IntSize((sourceSize.width * portraitScale).toInt(), (sourceSize.height * portraitScale).toInt())
            val portraitOrigin = IntOffset(((size.width - portraitSize.width) / 2).toInt(), (size.height * (if (cryoAttendants != null) .43f else .5f) - portraitSize.height / 2f).toInt())
            // 左右の人物は中央より大きく、少し下へ配置して手前の奥行きを表す。
            val attendantCell = cryoAttendants?.let { IntSize(it.width / 2, it.height) }
            val attendantScale = attendantCell?.let { min(size.width * .46f / it.width, size.height * .58f / it.height) } ?: 0f
            val attendantSize = attendantCell?.let { IntSize((it.width * attendantScale).toInt(), (it.height * attendantScale).toInt()) }
            // 月と草の相棒は同じ左下の位置へ、全身の縦横比を保って配置する。
            val companionScale = flyingCompanion?.let { min(size.width * .42f / it.width, size.height * .30f / it.height) } ?: 0f
            val companionSize = flyingCompanion?.let { IntSize((it.width * companionScale).toInt(), (it.height * companionScale).toInt()) }
            val companionOrigin = IntOffset((size.width * .02f).toInt(), (size.height * .60f).toInt())
            val companionTint = ColorFilter.tint(tint)
            // 風の魔法陣は人物の背後へ大きく置き、中心の空白で顔と胴体を見やすくする。
            val circleScale = anemoCircle?.let { min(size.width * 1.26f / it.width, size.height * .74f / it.height) } ?: 0f
            val circleSize = anemoCircle?.let { IntSize((it.width * circleScale).toInt(), (it.height * circleScale).toInt()) }
            val circleOrigin = circleSize?.let { IntOffset(((size.width - it.width) / 2f).toInt(), (size.height * .46f - it.height / 2f).toInt()) }
            // 中央の人物を拡大し、周辺の装飾は画面端へ広がる控えめな線として表示する。
            val splashScale = splashArtwork?.let { min(size.width * 1.42f / it.width, size.height * .82f / it.height) } ?: 0f
            val splashSize = splashArtwork?.let { IntSize((it.width * splashScale).toInt(), (it.height * splashScale).toInt()) }
            val splashOrigin = splashSize?.let { IntOffset(((size.width - it.width) / 2).toInt(), ((size.height - it.height) / 2).toInt()) }
            val emphasisPaint = Paint()
            val emphasisMask = splashSize?.let {
                Brush.radialGradient(0f to Color.Black, .50f to Color.Black, 1f to Color.Transparent,
                    center = Offset((splashOrigin!!.x + it.width * .52f), (splashOrigin.y + it.height * .55f)),
                    radius = it.width * .40f)
            }
            // 白い部分を描画時に透明へ変換し、背景色が人物の内側まで連続するようにする。
            val portraitTint = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                0f, 0f, 0f, 0f, tint.red * 255f,
                0f, 0f, 0f, 0f, tint.green * 255f,
                0f, 0f, 0f, 0f, tint.blue * 255f,
                -2.5f / 3f, -2.5f / 3f, -2.5f / 3f, 2.5f, 0f
            )))
            val lunarGlowTint = ColorFilter.tint(Color(0xFFCEFAFF))
            val glowOffsets = listOf(IntOffset(-3, 0), IntOffset(3, 0), IntOffset(0, -3), IntOffset(0, 3))
            val floralScale = lunarFloralArtwork?.let { min(size.width * 1.06f / it.width, size.height * .91f / it.height) } ?: 0f
            val floralSize = lunarFloralArtwork?.let { IntSize((it.width * floralScale).toInt(), (it.height * floralScale).toInt()) }
            val floralOrigin = floralSize?.let { IntOffset(((size.width - it.width) / 2f).toInt(), ((size.height - it.height) / 2f).toInt()) }
            val featuredScale = featuredArtwork?.let { min(size.width * .96f / it.width, size.height * .80f / it.height) } ?: 0f
            val featuredSize = featuredArtwork?.let { IntSize((it.width * featuredScale).toInt(), (it.height * featuredScale).toInt()) }
            val featuredOrigin = featuredSize?.let { IntOffset(((size.width - it.width) / 2f).toInt(), ((size.height - it.height) / 2f).toInt()) }
            // 六方向の枝分かれを持つ大きな結晶を中央の氷神の背後へ置く。
            val snowCrystal = Path().apply {
                val center = Offset(size.width * .5f, size.height * .40f)
                val radius = min(size.width * .59f, size.height * .30f)
                fun point(angle: Double, distance: Float) = Offset(center.x + cos(angle).toFloat() * distance,
                    center.y + sin(angle).toFloat() * distance)
                for (index in 0 until 6) {
                    val angle = Math.PI / 3 * index - Math.PI / 2
                    val tip = point(angle, radius)
                    moveTo(center.x, center.y); lineTo(tip.x, tip.y)
                    // 先端を小さなひし形へ整え、六方向の輪郭を揃える。
                    val tipBase = point(angle, radius * .83f)
                    val tipLeft = point(angle - .055, radius * .91f)
                    val tipRight = point(angle + .055, radius * .91f)
                    moveTo(tipBase.x, tipBase.y); lineTo(tipLeft.x, tipLeft.y)
                    lineTo(tip.x, tip.y); lineTo(tipRight.x, tipRight.y); close()
                    for (level in listOf(.46f, .70f)) {
                        val joint = point(angle, radius * level)
                        for (side in listOf(-1, 1)) {
                            val forkAngle = angle + side * Math.PI / 3
                            moveTo(joint.x, joint.y)
                            lineTo(joint.x + cos(forkAngle).toFloat() * radius * .24f,
                                joint.y + sin(forkAngle).toFloat() * radius * .24f)
                        }
                    }
                    val next = point(angle + Math.PI / 3, radius * .25f)
                    val inner = point(angle, radius * .25f)
                    moveTo(inner.x, inner.y); lineTo(next.x, next.y)
                }
            }
            // 枝は指定された二方向へ伸ばし、顔付近を避けて余白へ葉とツタを添える。
            val branchPath = Path().apply {
                moveTo(size.width * .47f, size.height * .51f)
                cubicTo(size.width * .65f, size.height * .55f, size.width * .78f, size.height * .42f, size.width * 1.04f, size.height * .49f)
                moveTo(size.width * .75f, size.height * .48f)
                quadraticTo(size.width * .83f, size.height * .39f, size.width * .96f, size.height * .38f)
                moveTo(size.width * .90f, size.height * .04f)
                cubicTo(size.width * .79f, size.height * .19f, size.width * .20f, size.height * .23f, -size.width * .04f, size.height * .49f)
                moveTo(size.width * .53f, size.height * .24f)
                quadraticTo(size.width * .37f, size.height * .17f, size.width * .18f, size.height * .20f)
                moveTo(-size.width * .03f, size.height * .12f)
                cubicTo(size.width * .15f, size.height * .08f, size.width * .22f, size.height * .12f, size.width * .35f, size.height * .09f)
                moveTo(size.width * .14f, size.height * .11f)
                quadraticTo(size.width * .15f, size.height * .04f, size.width * .27f, size.height * .02f)
                moveTo(size.width * 1.03f, size.height * .72f)
                cubicTo(size.width * .82f, size.height * .74f, size.width * .91f, size.height * .87f, size.width * .61f, size.height * .93f)
                moveTo(size.width * .87f, size.height * .81f)
                quadraticTo(size.width * .98f, size.height * .83f, size.width * 1.02f, size.height * .92f)
                moveTo(-size.width * .02f, size.height * .89f)
                quadraticTo(size.width * .24f, size.height * .94f, size.width * .44f, size.height * .87f)
            }
            val vinePath = Path().apply {
                moveTo(size.width * .50f, size.height * .54f)
                cubicTo(size.width * .72f, size.height * .42f, size.width * .69f, size.height * .60f, size.width * .92f, size.height * .50f)
                cubicTo(size.width * 1.08f, size.height * .43f, size.width * .80f, size.height * .41f, size.width * .87f, size.height * .48f)
                moveTo(size.width * .96f, size.height * .04f)
                cubicTo(size.width * .66f, size.height * .15f, size.width * .77f, size.height * .26f, size.width * .39f, size.height * .28f)
                cubicTo(size.width * .09f, size.height * .31f, size.width * .28f, size.height * .43f, -size.width * .02f, size.height * .47f)
                moveTo(size.width * .02f, size.height * .03f)
                cubicTo(size.width * .29f, size.height * .03f, size.width * .08f, size.height * .20f, size.width * .36f, size.height * .15f)
                moveTo(size.width * .98f, size.height * .61f)
                cubicTo(size.width * .79f, size.height * .75f, size.width * 1.05f, size.height * .77f, size.width * .76f, size.height * .88f)
                cubicTo(size.width * .52f, size.height * .98f, size.width * .52f, size.height * .83f, size.width * .62f, size.height * .86f)
                moveTo(size.width * .02f, size.height * .76f)
                cubicTo(size.width * .13f, size.height * .80f, size.width * .01f, size.height * .95f, size.width * .28f, size.height * .91f)
                cubicTo(size.width * .39f, size.height * .90f, size.width * .34f, size.height * .97f, size.width * .48f, size.height * .94f)
            }
            val leaves = Path().apply {
                for (anchor in listOf(Offset(.82f, .46f), Offset(.93f, .39f), Offset(.96f, .50f),
                    Offset(.79f, .15f), Offset(.65f, .22f), Offset(.33f, .29f), Offset(.15f, .37f), Offset(.24f, .20f),
                    Offset(.09f, .10f), Offset(.25f, .11f), Offset(.18f, .06f), Offset(.32f, .15f),
                    Offset(.86f, .75f), Offset(.89f, .83f), Offset(.74f, .90f), Offset(.63f, .92f),
                    Offset(.10f, .88f), Offset(.23f, .91f), Offset(.37f, .90f), Offset(.05f, .80f))) {
                    val x = size.width * anchor.x; val y = size.height * anchor.y
                    val spread = size.width * .045f
                    moveTo(x, y)
                    quadraticTo(x - spread, y - spread, x - spread * 1.5f, y - spread * 1.9f)
                    quadraticTo(x + spread * .3f, y - spread * 1.4f, x, y)
                    moveTo(x, y)
                    quadraticTo(x + spread, y + spread * .2f, x + spread * 1.6f, y - spread * .8f)
                    quadraticTo(x + spread * .2f, y - spread, x, y)
                }
            }
            onDrawBehind {
                // 専用の人物構図は原本の色と発光を保って表示する。
                if (featuredArtwork != null && featuredSize != null && featuredOrigin != null) {
                    drawImage(featuredArtwork, dstOffset = featuredOrigin, dstSize = featuredSize)
                }
                if (id == "rose") {
                    val iceColor = if (dark) Color(0xFFC0EEFF) else Color(0xFF4C92B6)
                    drawPath(snowCrystal, iceColor.copy(alpha = if (dark) .15f else .12f),
                        style = Stroke(size.width * .010f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    // 発光する縁を薄く重ねて中央の人物を強調し、全身の位置は固定する。
                    for (offset in glowOffsets) drawImage(portraitAtlas,
                        srcOffset = IntOffset(portraitIndex % 4 * cellSize.width + borderInset, portraitIndex / 4 * cellSize.height + borderInset),
                        srcSize = sourceSize, dstOffset = portraitOrigin + offset, dstSize = portraitSize,
                        alpha = if (dark) .12f else .06f, colorFilter = portraitTint)
                }
                if (id == "forest") {
                    drawPath(branchPath, tint.copy(alpha = if (dark) .22f else .16f), style = Stroke(size.width * .005f, cap = StrokeCap.Round))
                    drawPath(vinePath, tint.copy(alpha = if (dark) .28f else .20f), style = Stroke(size.width * .0023f, cap = StrokeCap.Round))
                    drawPath(leaves, tint.copy(alpha = if (dark) .24f else .18f), style = Stroke(size.width * .002f, cap = StrokeCap.Round))
                }
                if (id == "sky") {
                    if (lunarFloralArtwork != null && floralSize != null && floralOrigin != null) drawImage(
                        lunarFloralArtwork, dstOffset = floralOrigin, dstSize = floralSize,
                        alpha = 1f)
                    // 輪郭の近くへ薄い光を重ね、人物の位置を変えずに発光させる。
                    if (dark) for (offset in glowOffsets) {
                        if (lunarCompanion != null && companionSize != null) drawImage(lunarCompanion,
                            dstOffset = companionOrigin + offset, dstSize = companionSize, alpha = .09f, colorFilter = lunarGlowTint)
                    }
                }
                if (anemoCircle != null && circleSize != null && circleOrigin != null) drawImage(
                    image = anemoCircle, dstOffset = circleOrigin, dstSize = circleSize,
                    alpha = if (dark) .14f else .10f, colorFilter = portraitTint,
                )
                // 同じ透過線画をテーマ色へ着色し、表示モードによって輪郭の濃さを変える。
                if (portraitIndex >= 0 && splashArtwork == null && lunarFloralArtwork == null && featuredArtwork == null) drawImage(
                    image = portraitAtlas,
                    srcOffset = IntOffset(portraitIndex % 4 * cellSize.width + borderInset, portraitIndex / 4 * cellSize.height + borderInset),
                    srcSize = sourceSize,
                    dstOffset = portraitOrigin,
                    dstSize = portraitSize,
                    alpha = if (id == "sky" && dark) .94f else if (id == "forest") { if (dark) .88f else .76f } else if (cryoAttendants != null) { if (dark) .98f else .90f } else if (dark) .68f else .56f,
                    colorFilter = null
                )
                if (splashArtwork != null && splashSize != null && splashOrigin != null) drawImage(
                    image = splashArtwork,
                    dstOffset = splashOrigin,
                    dstSize = splashSize,
                    alpha = if (dark) .25f else .18f,
                    colorFilter = null,
                )
                // 同じ原本を柔らかなマスクで重ね、人物付近の線だけを強調する。
                if (splashArtwork != null && splashSize != null && splashOrigin != null && emphasisMask != null) {
                    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), emphasisPaint)
                    drawImage(image = splashArtwork, dstOffset = splashOrigin, dstSize = splashSize,
                        alpha = if (dark) .76f else .70f,
                        colorFilter = null)
                    drawRect(emphasisMask, blendMode = BlendMode.DstIn)
                    drawContext.canvas.restore()
                }
                if (flyingCompanion != null && companionSize != null) drawImage(
                    image = flyingCompanion,
                    dstOffset = companionOrigin,
                    dstSize = companionSize,
                    alpha = if (id == "sky" && dark) .96f else if (dark) .68f else .56f,
                    colorFilter = null,
                )
                if (cryoAttendants != null && attendantCell != null && attendantSize != null) {
                    for (index in 0..1) drawImage(
                        image = cryoAttendants,
                        srcOffset = IntOffset(index * attendantCell.width, 0),
                        srcSize = attendantCell,
                        dstOffset = IntOffset((size.width * (if (index == 0) .24f else .76f) - attendantSize.width / 2f).toInt(),
                            (size.height * .57f - attendantSize.height / 2f).toInt()),
                        dstSize = attendantSize,
                        alpha = if (dark) .60f else .52f,
                        colorFilter = null,
                    )
                }
                shapes.forEach { shape ->
                    if (shape.filled) drawPath(shape.path, tint.copy(alpha = opacity * shape.strength * if (isRose) .35f else .7f))
                    else drawPath(shape.path, tint.copy(alpha = opacity * shape.strength), style = stroke)
                }
            }
        })
    }
}

/** 座標を象徴ごとの単位空間から画面空間へ変換する。 */
private fun buildThemeMotif(id: String, center: Offset, radius: Float): List<MotifShape> {
    val shapes = mutableListOf<MotifShape>()
    fun path(filled: Boolean = false, strength: Float = 1f, block: Path.() -> Unit) {
        val path = Path().apply(block)
        path.transform(androidx.compose.ui.graphics.Matrix().apply {
            translate(center.x, center.y)
            scale(radius, radius)
        })
        shapes += MotifShape(path, filled, strength)
    }
    when (id) {
        "reference" -> {
            // 重なった花弁と茎でバラを表現する。
            for (layer in 0..2) for (index in 0..4) {
                val angle = Math.PI * 2 * index / 5 + layer * .6
                val x = cos(angle).toFloat(); val y = sin(angle).toFloat()
                val spread = 1f - layer * .26f
                path {
                    moveTo(x * spread * .18f - y * spread * .38f, y * spread * .18f + x * spread * .38f)
                    cubicTo(x * spread * 1.05f - y * spread * .7f, y * spread * 1.05f + x * spread * .7f,
                        x * spread * 1.05f + y * spread * .7f, y * spread * 1.05f - x * spread * .7f,
                        x * spread * .18f + y * spread * .38f, y * spread * .18f - x * spread * .38f)
                }
            }
            path { moveTo(.1f, .03f); cubicTo(-.5f, -.4f, -.6f, .5f, .1f, .4f)
                cubicTo(.7f, .25f, .2f, -.7f, -.35f, -.38f)
                moveTo(0f, .8f); quadraticTo(-.25f, 1.25f, -.05f, 1.65f)
                moveTo(-.12f, 1.2f); quadraticTo(-.8f, .8f, -.65f, 1.3f); quadraticTo(-.4f, 1.5f, -.12f, 1.2f) }
        }
    }
    return shapes
}
