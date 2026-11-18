package com.example.warptuner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * App-wide wrapper: theme, background, padding for system bars and the keyboard
 * (safeDrawingPadding) and a readable maximum width on wide screens.
 */
@Composable
fun AppRoot(content: @Composable () -> Unit) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(
                Modifier.fillMaxSize().safeDrawingPadding(),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(Modifier.widthIn(max = 640.dp)) { content() }
            }
        }
    }
}
