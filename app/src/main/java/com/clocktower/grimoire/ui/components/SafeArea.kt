package com.clocktower.grimoire.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.clocktower.grimoire.ui.platform.shellSafeBottomDp
import com.clocktower.grimoire.ui.platform.shellSafeTopDp

/**
 * The safe area, for the overlays that sit OUTSIDE the shell's own inset
 * padding — full-screen `Dialog`s, `Popup`s and `ModalBottomSheet`s.
 *
 * ## Why this file exists at all
 *
 * A storyteller could not close a full-screen show card: its FLIP / HOLD TO
 * CLOSE row was pinned to the *physical* bottom of the screen, so the phone's
 * home indicator covered all but a sliver of it and neither button could be
 * pressed. The card body ran under the status bar at the other end.
 *
 * ## The one mechanism, per platform — there is no second inset source
 *
 * * **Android**: `MainActivity` calls `enableEdgeToEdge()`, so Compose's own
 *   `WindowInsets` carry the real system-bar values. [Modifier.windowInsetsPadding]
 *   is therefore the whole story here, and it is *consumption-aware*: nested
 *   inside a container that already applied them (a `ModalBottomSheet`, a
 *   `safeDrawingPadding()` screen, a dialog window that fits system windows) it
 *   correctly adds nothing.
 * * **Web/wasm**: Compose knows nothing about `env(safe-area-inset-*)`, so
 *   `index.html` measures it into `window.__safeTop` / `__safeBottom` and
 *   `web/.../web/Main.kt` pads the app root by it. Dialog and popup layers are
 *   hosted at the *scene* root, above that padding, so they escape it — and
 *   only they need to re-apply it, which is what [shellSafeTopDp] /
 *   [shellSafeBottomDp] hand them. Same numbers, same source, read one layer up.
 *
 * The two halves are complementary and never both non-zero: the platform seam
 * returns 0 on Android (Compose already knows), and `windowInsetsPadding` adds
 * 0 on the web (Compose knows nothing). Applying both is correct everywhere.
 *
 * ## Which one to use
 *
 * * A pinned action row → `Modifier.padding(bottom = overlayBottomPadding())`,
 *   with the body above it padded by [bottomActionClearance] so the row never
 *   covers content.
 * * The scrolling content of a bottom sheet → the same
 *   [overlayBottomPadding] as its trailing padding.
 * * The root of a full-screen `Dialog`'s content → [overlaySafeAreaPadding].
 *
 * In-tree content needs NONE of this: on Android its screen already applies
 * `safeDrawingPadding()` (or sits in `GameShell`'s `Scaffold`), and on the web
 * the app root is padded. Adding an overlay inset there would double-pad.
 */

/**
 * Breathing room between a control pinned to the bottom of an overlay and the
 * home indicator / navigation bar below it. A finger aiming at the last 24 dp
 * of a phone screen hits the system gesture strip instead.
 */
val BottomActionMargin: Dp = 24.dp

/** The height the app gives a bottom-pinned control (its minimum touch target). */
val ActionRowHeight: Dp = 56.dp

/** The gap between an overlay's body and the action row pinned below it. */
val ActionRowGap: Dp = 16.dp

/**
 * Bottom padding for an action row pinned to the bottom edge of an overlay, or
 * for the trailing edge of a sheet's scrolling content.
 *
 * Pure, so `tools/uicheck` can measure it: [safeBottom] is whatever the
 * platform seam reported, and the result always clears it by at least
 * [BottomActionMargin].
 */
fun bottomActionPadding(safeBottom: Dp, margin: Dp = BottomActionMargin): Dp = margin + safeBottom

/**
 * Bottom padding for the BODY of an overlay that has an action row pinned
 * below it — the row's own padding, plus the row, plus a gap — so the body's
 * content is never hidden under the row.
 *
 * With a zero inset this is the 96 dp the show card used before the fix, so
 * nothing moves on a device with no home indicator.
 */
fun bottomActionClearance(
    safeBottom: Dp,
    rowHeight: Dp = ActionRowHeight,
    gap: Dp = ActionRowGap,
    margin: Dp = BottomActionMargin,
): Dp = bottomActionPadding(safeBottom, margin) + rowHeight + gap

/**
 * Extra bottom clearance a FULL-SCREEN `Dialog` needs, on top of everything
 * else — read in the shell's own composition, so call it *outside* the
 * `Dialog { }` lambda and pass the result in.
 *
 * An Android dialog window fits system windows by default: it is positioned
 * below the status bar and still sized to the whole display, so its content
 * runs exactly `statusBars` pixels past the bottom of the screen — and reports
 * ZERO insets inside, which makes `windowInsetsPadding` there a no-op. That is
 * how the read-only grimoire ended up with its only button 4 px tall, under the
 * home indicator (playtest D, P1-7). `decorFitsSystemWindows = false` is the
 * platform's own answer and does not exist in the multiplatform
 * `DialogProperties`, so the shell measures the offset instead.
 *
 * Zero on the web, where a dialog is hosted at the scene root with no offset
 * and [overlaySafeAreaPadding] already carries the measured safe area. Adding
 * it when the window turns out NOT to be offset costs a little empty space at
 * the bottom and never pushes anything off-screen, which is the trade to make.
 */
@Composable
fun dialogWindowBottomFix(): Dp {
    val bars = WindowInsets.systemBars.asPaddingValues()
    return bars.calculateTopPadding() + bars.calculateBottomPadding()
}

/** The bottom safe-area inset an overlay layer has to apply for itself. */
@Composable
fun overlaySafeBottom(): Dp = shellSafeBottomDp()

/** The top safe-area inset an overlay layer has to apply for itself. */
@Composable
fun overlaySafeTop(): Dp = shellSafeTopDp()

/**
 * The safe area as measured at the ROOT of the app, before anything consumed
 * it — provided by `GrimoireTheme` and read by the layers Compose cannot
 * measure from the inside.
 *
 * ## Why a `Dialog` needs this and `windowInsetsPadding` does not do it
 *
 * A `Dialog` is its own window. Its window fits system windows, so Compose
 * reports **zero** insets to everything inside it while the content still draws
 * edge to edge — measured on the reference device, a `FullScreenShow` asked for
 * 126 px of bottom padding and was given 0, and `⟳ FLIP` / `HOLD TO CLOSE` were
 * drawn in the gesture strip, sliced through the middle and untappable
 * (playtest B P1 #5). `DialogProperties(decorFitsSystemWindows = false)` would
 * fix it on Android and does not exist in Compose Multiplatform, which the same
 * source has to compile for. So the numbers are captured one layer up, where
 * they are real, and carried into the dialog by composition — which crosses the
 * window boundary exactly as `MaterialTheme` does.
 */
val LocalRootSafeTop: ProvidableCompositionLocal<Dp> = staticCompositionLocalOf { 0.dp }

/** The bottom half of [LocalRootSafeTop] — the home indicator's own strip. */
val LocalRootSafeBottom: ProvidableCompositionLocal<Dp> = staticCompositionLocalOf { 0.dp }

/** The larger of the two inset sources — pure, so `tools/uicheck` can measure it. */
fun dialogSafeInset(overlay: Dp, root: Dp): Dp = if (root > overlay) root else overlay

/**
 * How far a full-screen `Dialog`'s content is pushed DOWN by its own window,
 * and therefore how far it overflows the bottom of the screen.
 *
 * An Android dialog window fits system windows, so the platform insets its TOP
 * by the status bar — and then hands the content the whole screen's height as
 * its constraint. The content is 136 px too tall and starts 136 px too low, so
 * everything pinned to its bottom edge is off screen by the same 136 px.
 * Measured: `FullScreenShow`'s action row asked for 56 dp of clearance and
 * landed 12 px from the physical bottom, still under the home indicator.
 *
 * The amount is exactly what the window already consumed at the top: the root
 * inset minus whatever this platform's overlay layers still have to apply
 * themselves. Zero on the web, where dialogs are hosted at the scene root and
 * nothing is consumed.
 */
fun dialogTopConsumed(root: Dp, overlay: Dp): Dp = if (root > overlay) root - overlay else 0.dp

/** [overlaySafeTop] for content inside a `Dialog`'s own window. */
@Composable
fun dialogSafeTop(): Dp = dialogSafeInset(overlaySafeTop(), LocalRootSafeTop.current)

/** [overlaySafeBottom] for content inside a `Dialog`'s own window. */
@Composable
fun dialogSafeBottom(): Dp = dialogSafeInset(overlaySafeBottom(), LocalRootSafeBottom.current)

/** [dialogTopConsumed] with the live insets already read. */
@Composable
fun dialogTopOverflow(): Dp = dialogTopConsumed(LocalRootSafeTop.current, overlaySafeTop())

/** [bottomActionPadding] with the live inset already read. */
@Composable
fun overlayBottomPadding(margin: Dp = BottomActionMargin): Dp =
    bottomActionPadding(overlaySafeBottom(), margin)

/** [bottomActionClearance] with the live inset already read. */
@Composable
fun overlayBodyClearance(): Dp = bottomActionClearance(overlaySafeBottom())

/**
 * Keeps the content of a full-screen `Dialog` out of the status bar, the
 * navigation bar and the home indicator on every platform.
 *
 * Put it on the content INSIDE the backdrop, never on the backdrop itself:
 * the app paints its own background edge to edge (no black bands under the
 * notch) and only what can be read or pressed moves inwards.
 */
@Composable
fun Modifier.overlaySafeAreaPadding(): Modifier =
    this
        .windowInsetsPadding(WindowInsets.systemBars)
        .padding(
            // The window already inset the top on Android; on the web it did
            // not, and `overlaySafeTop` is the env() value. Either way the
            // bottom carries the overflow the window's own top inset caused.
            top = overlaySafeTop(),
            bottom = dialogSafeBottom() + dialogTopOverflow(),
        )
