@file:Suppress("UNUSED_PARAMETER", "unused", "PackageDirectoryMismatch")

package androidx.activity.result.contract

import android.net.Uri

abstract class ActivityResultContract<I, O>

class ActivityResultContracts {
    class OpenDocument : ActivityResultContract<Array<String>, Uri?>()
    class GetContent : ActivityResultContract<String, Uri?>()
}
