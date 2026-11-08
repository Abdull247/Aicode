package com.aicode.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val ToolGridIcon: ImageVector
    get() {
        val cached = _toolGridIcon
        if (cached != null) return cached
        val created = ImageVector.Builder(
            name = "ToolGrid",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            val stroke = SolidColor(Color.Black)
            val width = 1.75f
            fun node(cx: Float, cy: Float) {
                path(
                    fill = null,
                    stroke = stroke,
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round
                ) {
                    moveTo(cx + 2.6f, cy)
                    arcToRelative(2.6f, 2.6f, 0f, true, true, -5.2f, 0f)
                    arcToRelative(2.6f, 2.6f, 0f, true, true, 5.2f, 0f)
                    close()
                }
            }
            node(7f, 7f)
            node(17f, 7f)
            node(7f, 17f)
            node(17f, 17f)
            path(
                fill = null,
                stroke = stroke,
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(7f, 9.7f)
                lineTo(7f, 14.3f)
                moveTo(17f, 9.7f)
                lineTo(17f, 14.3f)
                moveTo(9.7f, 7f)
                lineTo(14.3f, 7f)
                moveTo(9.7f, 17f)
                lineTo(14.3f, 17f)
            }
        }.build()
        _toolGridIcon = created
        return created
    }

private var _toolGridIcon: ImageVector? = null
