package com.krisna.an1adz.core.signature

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

data class SignatureStroke(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float
)

