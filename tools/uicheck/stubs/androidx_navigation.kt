@file:Suppress("UNUSED_PARAMETER", "unused", "PackageDirectoryMismatch")

package androidx.navigation

open class NavController {
    fun navigate(route: String, builder: NavOptionsBuilder.() -> Unit = {}) {}
    fun popBackStack(): Boolean = true
    fun popBackStack(route: String, inclusive: Boolean, saveState: Boolean = false): Boolean = true
}

open class NavHostController : NavController()

class NavOptionsBuilder {
    var launchSingleTop: Boolean = false
    fun popUpTo(route: String, popUpToBuilder: PopUpToBuilder.() -> Unit = {}) {}
}

class PopUpToBuilder {
    var inclusive: Boolean = false
    var saveState: Boolean = false
}

class NavGraphBuilder

class NavBackStackEntry
