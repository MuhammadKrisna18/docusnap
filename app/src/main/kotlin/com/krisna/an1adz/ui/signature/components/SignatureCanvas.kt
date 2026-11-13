package com.krisna.an1adz.ui.signature.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.krisna.an1adz.core.signature.SignatureStroke

@Composable
fun SignatureCanvas(
    strokes: List<SignatureStroke>,
    selectedColor: Color,
    selectedStrokeWidth: Float,
    onStrokeCompleted: (List<Offset>) -> Unit,
    onCanvasSizeChanged: (IntSize) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentPoints = remember { mutableStateListOf<Offset>() }
    var hasDrawnAny by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .onSizeChanged { onCanvasSizeChanged(it) }
    ) {
        if (strokes.isEmpty() && currentPoints.isEmpty()) {
            Text(
                text = "Goreskan tanda tangan di sini",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .pointerInput(selectedColor, selectedStrokeWidth) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentPoints.clear()
                            currentPoints.add(offset)
                            hasDrawnAny = true
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            currentPoints.add(change.position)
                        },
                        onDragEnd = {
                            if (currentPoints.isNotEmpty()) {
                                onStrokeCompleted(currentPoints.toList())
                                currentPoints.clear()
                            }
                        },
                        onDragCancel = {
                            currentPoints.clear()
                        }
                    )
                }
        ) {
            strokes.forEach { stroke ->
                if (stroke.points.size > 1) {
                    val path = Path().apply {
                        moveTo(stroke.points[0].x, stroke.points[0].y)
                        for (i in 1 until stroke.points.size) {
                            val prev = stroke.points[i - 1]
                            val curr = stroke.points[i]
                            val midX = (prev.x + curr.x) / 2f
                            val midY = (prev.y + curr.y) / 2f
                            quadraticTo(prev.x, prev.y, midX, midY)
                        }
                        lineTo(stroke.points.last().x, stroke.points.last().y)
                    }
                    drawPath(
                        path = path,
                        color = stroke.color,
                        style = Stroke(
                            width = stroke.strokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                } else if (stroke.points.size == 1) {
                    drawCircle(
                        color = stroke.color,
                        radius = stroke.strokeWidth / 2f,
                        center = stroke.points[0]
                    )
                }
            }

            if (currentPoints.size > 1) {
                val path = Path().apply {
                    moveTo(currentPoints[0].x, currentPoints[0].y)
                    for (i in 1 until currentPoints.size) {
                        val prev = currentPoints[i - 1]
                        val curr = currentPoints[i]
                        val midX = (prev.x + curr.x) / 2f
                        val midY = (prev.y + curr.y) / 2f
                        quadraticTo(prev.x, prev.y, midX, midY)
                    }
                    lineTo(currentPoints.last().x, currentPoints.last().y)
                }
                drawPath(
                    path = path,
                    color = selectedColor,
                    style = Stroke(
                        width = selectedStrokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            } else if (currentPoints.size == 1) {
                drawCircle(
                    color = selectedColor,
                    radius = selectedStrokeWidth / 2f,
                    center = currentPoints[0]
                )
            }
        }
    }
}

