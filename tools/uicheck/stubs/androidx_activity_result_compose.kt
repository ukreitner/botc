@file:Suppress("UNUSED_PARAMETER", "unused", "PackageDirectoryMismatch")

package androidx.activity.compose

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable

@Composable
fun <I, O> rememberLauncherForActivityResult(
    contract: ActivityResultContract<I, O>,
    onResult: (O) -> Unit,
): ActivityResultLauncher<I> = object : ActivityResultLauncher<I>() {
    override fun launch(input: I) {}
}
