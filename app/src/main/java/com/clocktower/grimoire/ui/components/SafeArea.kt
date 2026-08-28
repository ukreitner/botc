package com.clocktower.grimoire.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.clocktower.grimoire.ui.platform.dialogDecorTopDp
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

/** The bottom safe-area inset an overlay layer has to apply for itself. */
@Composable
fun overlaySafeBottom(): Dp = shellSafeBottomDp()

/** The top safe-area inset an overlay layer has to apply for itself. */
@Composable
fun overlaySafeTop(): Dp = shellSafeTopDp()

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
 *
 * **Read [rememberOverlayInsets] before you use this one.** A `Dialog` window
 * that fits system windows reports NO insets to the Compose tree inside it, so
 * `windowInsetsPadding` there resolves to zero and this overload silently does
 * nothing (playtest A-2: the hand-out's "Start over" / "Finish later" row was
 * drawn 200 px below the screen when the same screen was opened through the
 * dialog). Prefer the [OverlayInsets] overload, whose numbers are measured
 * outside the dialog where they still exist.
 */
@Composable
fun Modifier.overlaySafeAreaPadding(): Modifier =
    this
        .windowInsetsPadding(WindowInsets.systemBars)
        .padding(top = overlaySafeTop(), bottom = overlaySafeBottom())

/**
 * The safe area as two plain numbers, captured IN-TREE so an overlay hosted
 * above the tree can still apply it.
 *
 * Insets are a property of the window. A `Dialog`'s content lives in a second
 * window, and unless that window is explicitly taken edge to edge Compose
 * reports zero insets inside it — which is a per-platform switch this shared UI
 * cannot reach. Numbers, measured on the app's own window, cross that boundary
 * unchanged.
 */
@Immutable
data class OverlayInsets(val top: Dp, val bottom: Dp)

/**
 * [OverlayInsets] for the window this composable is in.
 *
 * `safeContent`, not `safeDrawing`: the strip the home indicator swallows
 * (`mandatorySystemGestures`, 84 px on the reference phone) is BIGGER than the
 * navigation bar (63 px), and a button whose centre lands in it cannot be
 * pressed at all — which is exactly what `tools/emu/ui.py audit` measures.
 */
@Composable
fun rememberOverlayInsets(): OverlayInsets {
    val measured = WindowInsets.safeContent.asPaddingValues()
    return OverlayInsets(
        top = measured.calculateTopPadding() + overlaySafeTop(),
        bottom = measured.calculateBottomPadding() + overlaySafeBottom(),
    )
}

/** [overlaySafeAreaPadding] with insets [rememberOverlayInsets] already measured. */
fun Modifier.overlaySafeAreaPadding(insets: OverlayInsets): Modifier =
    this.padding(top = insets.top, bottom = insets.bottom)

/**
 * [OverlayInsets] for the content of a full-screen `Dialog`, corrected for what
 * the dialog's own window already did.
 *
 * Measured on the reference phone (playtest A-2): the dialog is drawn from
 * y=136 — the platform pushed it below the status bar — while Compose inside it
 * measures against the full 2400 px and reports no insets at all. Everything
 * the window took off the top therefore falls off the bottom, so [dialogDecorTopDp]
 * moves exactly that much padding from one end to the other. Zero on the web,
 * where a dialog is hosted at the scene root and nothing is moved.
 *
 * Call it OUTSIDE the `Dialog`, like [rememberOverlayInsets].
 */
@Composable
fun rememberDialogInsets(): OverlayInsets {
    val safe = rememberOverlayInsets()
    val decor = dialogDecorTopDp()
    return OverlayInsets(
        top = (safe.top - decor).coerceAtLeast(0.dp),
        bottom = safe.bottom + decor,
    )
}

/**
 * Bottom padding for a control pinned below the scrolling content of a
 * `ModalBottomSheet`, with [insets] measured by [rememberOverlayInsets] at the
 * sheet's CALL SITE.
 *
 * Measured on the reference phone: a `ModalBottomSheet` leaves 8 px under its
 * content and reports its insets as already consumed, so neither
 * [overlayBottomPadding] (24 dp of margin and nothing else) nor a nested
 * [windowInsetsPadding] could reach the 84 px the home indicator swallows —
 * the checklist's Close button still ended 13 px inside the strip. The numbers
 * have to come from outside the sheet, exactly as they do for a `Dialog`.
 */
fun Modifier.sheetActionPadding(
    insets: OverlayInsets,
    margin: Dp = BottomActionMargin,
): Modifier = this.padding(bottom = bottomActionPadding(insets.bottom, margin))
