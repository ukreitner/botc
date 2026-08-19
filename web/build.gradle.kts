import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec

plugins {
    // Kept newer than the Android app on purpose: Compose Multiplatform
    // rewrote browser input handling after 1.7 (stuck hover/pressed states,
    // cancelled taps), so the web target tracks the latest stable CMP.
    kotlin("multiplatform") version "2.3.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
    id("org.jetbrains.compose") version "1.11.1"
}

// Use the system Node and Yarn (present locally and on CI runners) so the
// build never depends on nodejs.org being reachable.
plugins.withType<NodeJsRootPlugin> {
    the<NodeJsEnvSpec>().download.set(false)
}
plugins.withType<YarnPlugin> {
    the<YarnRootEnvSpec>().download.set(false)
}
// Kotlin 2.3 gives the wasm target its own node/yarn plugins.
plugins.withType<WasmNodeJsRootPlugin> {
    the<WasmNodeJsEnvSpec>().download.set(false)
}
plugins.withType<WasmYarnPlugin> {
    the<WasmYarnRootEnvSpec>().download.set(false)
}

kotlin {
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "grimoire.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        val wasmJsMain by getting {
            // Real app sources — the browser substitutes live in
            // src/wasmJsMain/kotlin under different file names.
            kotlin.srcDir("../engine/src/main/kotlin")
            kotlin.srcDir("../app/src/main/java")
            kotlin.exclude(
                "com/clocktower/engine/Platform.kt", // JVM resources/clock
                "com/clocktower/grimoire/IconLoader.kt", // Android bitmaps
                "com/clocktower/grimoire/GrimoireApp.kt", // Android Application
                "com/clocktower/grimoire/MainActivity.kt", // Android activity + nav
                "com/clocktower/grimoire/UpdateBanner.kt", // APK self-updater
                "com/clocktower/grimoire/data/Persistence.kt", // DataStore
                "com/clocktower/grimoire/ui/GameViewModel.kt", // AndroidViewModel
                "com/clocktower/grimoire/ui/platform/Platform.kt", // Android seam
                "com/clocktower/grimoire/ui/components/IconStore.kt", // JVM sync
            )
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-browser:0.3")
            }
        }
    }
}
