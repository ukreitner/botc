plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

sourceSets {
    main {
        kotlin {
            srcDir("stubs")                                // Android framework stand-ins
            srcDir("../../engine/src/main/kotlin")         // real engine sources
            srcDir("../../app/src/main/java")              // the app UI under test
            // Android-only image loading (BitmapFactory/assets); the stubs
            // directory provides a JVM stand-in for GrimoireApp instead.
            exclude("**/IconLoader.kt")
            exclude("**/GrimoireApp.kt")
            // Self-updater (HttpURLConnection + FileProvider + BuildConfig);
            // the stubs directory provides a no-op banner instead.
            exclude("**/UpdateBanner.kt")
        }
        resources {
            srcDir("../../engine/src/main/resources")
        }
    }
}

// androidx.* modules live only on Google's Maven repository, which this
// verification build deliberately avoids. The redirecting POMs that CMP
// artifacts reference are excluded; anything actually needed at compile
// time is covered by the stubs directory.
configurations.all {
    exclude(group = "androidx.annotation")
    exclude(group = "androidx.collection")
    exclude(group = "androidx.lifecycle")
    exclude(group = "org.jetbrains.androidx.lifecycle")
    exclude(group = "org.jetbrains.androidx.navigation")
}

dependencies {
    val compose = "1.7.3"
    implementation("org.jetbrains.compose.runtime:runtime-desktop:$compose")
    implementation("org.jetbrains.compose.foundation:foundation-desktop:$compose")
    implementation("org.jetbrains.compose.ui:ui-desktop:$compose")
    implementation("org.jetbrains.compose.ui:ui-graphics-desktop:$compose")
    implementation("org.jetbrains.compose.ui:ui-text-desktop:$compose")
    implementation("org.jetbrains.compose.ui:ui-unit-desktop:$compose")
    implementation("org.jetbrains.compose.material3:material3-desktop:$compose")
    implementation("org.jetbrains.compose.material:material-icons-extended-desktop:$compose")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}
