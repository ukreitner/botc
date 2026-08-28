@file:Suppress("UNUSED_PARAMETER", "unused", "PackageDirectoryMismatch")

package androidx.activity.compose

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable

fun ComponentActivity.setContent(content: @Composable () -> Unit) {}

/** WP: `MainActivity` guards Back inside a running game (playtest B P1 #10). */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {}
