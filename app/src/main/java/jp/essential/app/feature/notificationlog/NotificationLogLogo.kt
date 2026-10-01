package jp.essential.app.feature.notificationlog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** ホームと通知ログ画面で共通のベルロゴを描く。 */
internal fun DrawScope.drawNotificationLogLogo(tint: Color, stroke: Stroke) {
    val bell = Path().apply {
        moveTo(size.width * 0.20f, size.height * 0.72f)
        lineTo(size.width * 0.28f, size.height * 0.60f)
        lineTo(size.width * 0.28f, size.height * 0.38f)
        cubicTo(size.width * 0.28f, size.height * 0.08f, size.width * 0.72f, size.height * 0.08f, size.width * 0.72f, size.height * 0.38f)
        lineTo(size.width * 0.72f, size.height * 0.60f)
        lineTo(size.width * 0.80f, size.height * 0.72f)
        close()
    }
    drawPath(bell, tint, style = stroke)
    drawCircle(tint, size.minDimension * 0.055f, Offset(center.x, size.height * 0.84f))
}

@Composable
internal fun NotificationLogLogo() {
    val tint = Color(0xFF78D9EF)
    Surface(color = tint.copy(alpha = 0.12f), shape = RoundedCornerShape(16.dp),
        modifier = Modifier.size(48.dp).testTag("notification-log-logo")
            .semantics { contentDescription = "通知ログのロゴ" }) {
        Canvas(Modifier.padding(10.dp)) {
            drawNotificationLogLogo(tint, Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
