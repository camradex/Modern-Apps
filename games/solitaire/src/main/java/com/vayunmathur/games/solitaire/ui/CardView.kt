package com.vayunmathur.games.solitaire.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vayunmathur.games.solitaire.data.Card

val CARD_WIDTH = 60.dp
val CARD_HEIGHT = 84.dp
private val RedColor = Color(0xFFCC0000)

@Composable
fun CardFace(card: Card, modifier: Modifier = Modifier, cardWidth: Dp = CARD_WIDTH, cardHeight: Dp = CARD_HEIGHT) {
    val color = if (card.suit.isRed) RedColor else Color.Black
    Surface(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color.Black)
    ) {
        Box(Modifier.padding(4.dp)) {
            Text(
                text = card.rank.display,
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart)
            )
            Text(
                text = card.suit.symbol,
                color = color,
                fontSize = 24.sp,
                modifier = Modifier.align(Alignment.Center)
            )
            Text(
                text = "${card.rank.display}${card.suit.symbol}",
                color = color,
                fontSize = 8.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .graphicsLayer { rotationZ = 180f }
            )
        }
    }
}

@Composable
fun CardBack(modifier: Modifier = Modifier, cardWidth: Dp = CARD_WIDTH, cardHeight: Dp = CARD_HEIGHT) {
    Surface(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1565C0),
        border = BorderStroke(1.dp, Color.Black)
    ) {
        Box(
            Modifier
                .padding(4.dp)
                .background(Color(0xFF0D47A1), RoundedCornerShape(4.dp))
        )
    }
}

@Composable
fun EmptySlot(modifier: Modifier = Modifier, label: String = "", cardWidth: Dp = CARD_WIDTH, cardHeight: Dp = CARD_HEIGHT) {
    val dashedStroke = Stroke(
        width = 2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
    )
    Box(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
            .drawBehind {
                drawRoundRect(
                    color = Color.Gray,
                    style = dashedStroke,
                    cornerRadius = CornerRadius(8.dp.toPx())
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (label.isNotEmpty()) {
            Text(label, color = Color.Gray, fontSize = 16.sp)
        }
    }
}
