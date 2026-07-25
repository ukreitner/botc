@file:Suppress("UNUSED_PARAMETER", "unused", "PackageDirectoryMismatch")

package androidx.lifecycle

import android.app.Application

// The real ViewModel/viewModelScope come from the multiplatform
// lifecycle-viewmodel artifact; only the Android-specific subclass is stubbed.
open class AndroidViewModel(private val application: Application) : ViewModel() {
    @Suppress("UNCHECKED_CAST")
    fun <T : Application> getApplication(): T = application as T
}
