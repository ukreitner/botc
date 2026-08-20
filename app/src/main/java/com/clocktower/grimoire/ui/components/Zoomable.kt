package com.clocktower.grimoire.ui.components

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Zoom-and-pan state shared by the grimoire and notes circles: pinch (or
 * the corner buttons) to scale, drag to pan, one tap to recenter.
 */
class ZoomState(scale: Float = 1f, offsetX: Float = 0f, offsetY: Float = 0f) {
    var scale by mutableFloatStateOf(scale)
    var offsetX by mutableFloatStateOf(offsetX)
    var offsetY by mutableFloatStateOf(offsetY)

    val isDefault: Boolean
        get() = scale == 1f && offsetX == 0f && offsetY == 0f

    fun zoomBy(factor: Float) {
        scale = (scale * factor).coerceIn(0.6f, 2.5f)
    }

    fun panBy(dx: Float, dy: Float) {
        offsetX += dx
        offsetY += dy
    }

    fun reset() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    companion object {
        val Saver = listSaver<ZoomState, Float>(
            save = { listOf(it.scale, it.offsetX, it.offsetY) },
            restore = { ZoomState(it[0], it[1], it[2]) },
        )
    }
}

@Composable
fun rememberZoomState(): ZoomState = rememberSaveable(saver = ZoomState.Saver) { ZoomState() }

/** Pinch-zoom and pan gestures feeding [state]; put on the outer container. */
fun Modifier.zoomGestures(state: ZoomState): Modifier = pointerInput(Unit) {
    detectTransformGestures { _, pan, zoom, _ ->
        state.zoomBy(zoom)
        state.panBy(pan.x, pan.y)
    }
}

/** Applies the zoom/pan transform; put on the content that should scale. */
fun Modifier.zoomTransform(state: ZoomState): Modifier = graphicsLayer {
    scaleX = state.scale
    scaleY = state.scale
    translationX = state.offsetX
    translationY = state.offsetY
}

/**
 * Tap-based zoom buttons — reliable on every platform (browser pinch
 * gestures are flaky); pinch still works where the platform allows.
 */
@Composable
fun ZoomControls(state: ZoomState, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        FilledTonalIconButton(
            onClick = { state.zoomBy(1.3f) },
            modifier = Modifier.size(44.dp),
        ) {
            Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom in")
        }
        FilledTonalIconButton(
            onClick = { state.zoomBy(1 / 1.3f) },
            modifier = Modifier.size(44.dp),
        ) {
            Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom out")
        }
        if (!state.isDefault) {
            FilledTonalIconButton(
                onClick = { state.reset() },
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    Icons.Filled.CenterFocusStrong,
                    contentDescription = "Reset zoom and recenter",
                )
            }
        }
    }
}
