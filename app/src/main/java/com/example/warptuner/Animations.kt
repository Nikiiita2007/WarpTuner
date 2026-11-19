package com.example.warptuner

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle

/** Кнопка, которая слегка «вдавливается» при нажатии. */
@Composable
fun ActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale"
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        content = content
    )
}

/** Текст статуса: при смене значения старый плавно гаснет, новый выезжает снизу. */
@Composable
fun AnimatedNotice(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current
) {
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 4 }) togetherWith fadeOut(tween(120))
        },
        label = "notice"
    ) { value ->
        if (value.isNotEmpty()) Text(value, style = style)
    }
}

/** Плавное появление блока при первом показе. */
@Composable
fun AppearAnimated(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visible,
        modifier = modifier,
        enter = fadeIn(tween(250)) + expandVertically(tween(250)),
        exit = fadeOut(tween(150))
    ) { content() }
}
