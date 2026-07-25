@file:Suppress("UNUSED_PARAMETER", "unused", "PackageDirectoryMismatch")

package androidx.activity.compose

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable

fun ComponentActivity.setContent(content: @Composable () -> Unit) {}
