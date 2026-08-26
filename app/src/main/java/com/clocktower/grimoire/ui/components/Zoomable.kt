package com.clocktower.grimoire.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** Smallest and largest scale. 4x is useful now that glyphs are >= 11 sp. */
private const val MIN_SCALE = 0.8f
private const val MAX_SCALE = 4f

/** A drag only pans once the content is actually magnified; below this, taps win. */
private const val PAN_THRESHOLD = 1.02f

/** Fraction of the content that must stay on screen — you cannot fling it away. */
private const val KEEP_VISIBLE = 0.6f

/**
 * Zoom-and-pan state shared by the grimoire and notes circles.
 *
 * Fixes from grimoire-and-seats P2-20: zoom is anchored at the CENTROID of the
 * pinch (it used to pull the seat you pinched toward the screen centre), pan is
 * clamped so the ring can never be flung off-screen, a one-finger drag is inert
 * below [PAN_THRESHOLD] so a slightly-slipped tap opens the seat instead of
 * scrolling the grimoire, and the cap is 4x.
 */
class ZoomState(scale: Float = 1f, offsetX: Float = 0f, offsetY: Float = 0f) {
    var scale by mutableFloatStateOf(scale)
    var offsetX by mutableFloatStateOf(offsetX)
    var offsetY by mutableFloatStateOf(offsetY)

    /** Content size in px, published by [zoomGestures] so pan can be clamped. */
    var contentWidth by mutableFloatStateOf(0f)
    var contentHeight by mutableFloatStateOf(0f)

    val isDefault: Boolean
        get() = scale == 1f && offsetX == 0f && offsetY == 0f

    /** Zooms about [centroid] (px, relative to the content box), keeping it fixed. */
    fun zoomAbout(centroid: Offset, factor: Float, panX: Float = 0f, panY: Float = 0f) {
        val old = scale
        val new = (old * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val cx = centroid.x - contentWidth / 2f
        val cy = centroid.y - contentHeight / 2f
        // Keep the point under the fingers where it is: the layer scales about
        // its own centre, so the offset must absorb the difference.
        offsetX = cx - (cx - offsetX) * (new / old) + panX
        offsetY = cy - (cy - offsetY) * (new / old) + panY
        scale = new
        clampPan()
    }

    fun zoomBy(factor: Float) {
        zoomAbout(Offset(contentWidth / 2f, contentHeight / 2f), factor)
    }

    fun panBy(dx: Float, dy: Float) {
        offsetX += dx
        offsetY += dy
        clampPan()
    }

    /** Never let more than 40% of the ring leave the viewport. */
    fun clampPan() {
        if (contentWidth <= 0f || contentHeight <= 0f) return
        val maxX = (contentWidth * scale * (1f - KEEP_VISIBLE) / 2f).coerceAtLeast(0f) +
            (contentWidth * (scale - 1f) / 2f).coerceAtLeast(0f)
        val maxY = (contentHeight * scale * (1f - KEEP_VISIBLE) / 2f).coerceAtLeast(0f) +
            (contentHeight * (scale - 1f) / 2f).coerceAtLeast(0f)
        offsetX = offsetX.coerceIn(-maxX, maxX)
        offsetY = offsetY.coerceIn(-maxY, maxY)
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

/** Pinch-zoom, clamped pan and double-tap-to-fit; put on the outer container. */
fun Modifier.zoomGestures(state: ZoomState): Modifier = this
    .pointerInput(Unit) {
        state.contentWidth = size.width.toFloat()
        state.contentHeight = size.height.toFloat()
        detectTransformGestures { centroid, pan, zoom, _ ->
            state.contentWidth = size.width.toFloat()
            state.contentHeight = size.height.toFloat()
            if (zoom != 1f) {
                state.zoomAbout(centroid, zoom, pan.x, pan.y)
            } else if (state.scale > PAN_THRESHOLD) {
                // A one-finger drag pans ONLY when there is something to pan.
                state.panBy(pan.x, pan.y)
            }
        }
    }
    .pointerInput(Unit) {
        detectTapGestures(
            onDoubleTap = { tap ->
                state.contentWidth = size.width.toFloat()
                state.contentHeight = size.height.toFloat()
                if (state.scale > PAN_THRESHOLD) state.reset() else state.zoomAbout(tap, 2f)
            },
        )
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
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom in")
        }
        FilledTonalIconButton(
            onClick = { state.zoomBy(1 / 1.3f) },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom out")
        }
        if (!state.isDefault) {
            FilledTonalIconButton(
                onClick = { state.reset() },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    Icons.Filled.CenterFocusStrong,
                    contentDescription = "Reset zoom and recenter",
                )
            }
        }
    }
}
