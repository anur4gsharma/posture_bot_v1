package com.posturebot.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.posturebot.app.domain.statemachine.PostureState

@Composable
fun PostureStateCard(state: PostureState) {

    val (targetColor, label, emoji) = when (state) {
        PostureState.Idle        -> Triple(Color(0xFF3B82F6), "Ready", "●")
        PostureState.Good        -> Triple(Color(0xFF16A34A), "Good Posture", "✓")
        PostureState.Warning     -> Triple(Color(0xFFD97706), "Adjust Posture", "⚠")
        PostureState.Bad         -> Triple(Color(0xFFDC2626), "Fix Your Posture!", "✗")
        PostureState.Calibrating -> Triple(Color(0xFF6B7280), "Calibrating…", "◌")
        PostureState.Stopped     -> Triple(Color(0xFF6B7280), "Stopped", "■")
    }

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 500),
        label = "stateColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = animatedColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedContent(
                targetState = emoji,
                transitionSpec = {
                    (fadeIn(tween(300)) + scaleIn(initialScale = 0.8f))
                        .togetherWith(fadeOut(tween(200)) + scaleOut(targetScale = 0.8f))
                },
                label = "emoji"
            ) { currentEmoji ->
                Text(
                    text = currentEmoji,
                    fontSize = 28.sp,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            AnimatedContent(
                targetState = label,
                transitionSpec = {
                    fadeIn(tween(300)).togetherWith(fadeOut(tween(200)))
                },
                label = "label"
            ) { currentLabel ->
                Text(
                    text = currentLabel,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
