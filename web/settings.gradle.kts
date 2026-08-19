// The web (WebAssembly) app: the same engine and Compose UI sources as
// the Android app, compiled with Compose Multiplatform for the browser
// and wrapped as an installable PWA. Standalone build (like tools/uicheck)
// so the Android build remains untouched.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}
dependencyResolutionManagement {
    // PREFER (not FAIL): the Kotlin/Wasm plugin registers its own ivy repo
    // for the binaryen optimizer distribution.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        // CMP 1.10+ ships its multiplatform artifacts under androidx.*
        // coordinates on Google's Maven repository.
        google()
        // The binaryen distribution the Kotlin/Wasm plugin needs, mirrored
        // here because settings-mode ignores plugin-added repositories.
        ivy("https://github.com/WebAssembly/binaryen/releases/download") {
            name = "binaryen"
            patternLayout {
                artifact("version_[revision]/binaryen-version_[revision]-[classifier].[ext]")
            }
            metadataSources { artifact() }
            content { includeGroup("com.github.webassembly") }
        }
        // Node.js dist repo the toolchain registers (unused with
        // download=false, but must resolve during configuration).
        ivy("https://nodejs.org/dist") {
            name = "nodejs"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeGroup("org.nodejs") }
        }
    }
}
rootProject.name = "grimoire-web"
