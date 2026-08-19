plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.clocktower.grimoire"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.clocktower.grimoire"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        // The commit this build came from; the in-app updater compares it
        // against the rolling GitHub release to spot newer builds.
        buildConfigField("String", "BUILD_SHA", "\"${findProperty("buildSha") ?: "dev"}\"")
    }

    // A persistent key (CI: decoded from the APK_KEYSTORE_B64 secret) so
    // every published APK carries the SAME signature — Android only allows
    // in-place updates when signatures match. Without the env vars set,
    // builds fall back to the machine's throwaway debug key as usual.
    val grimoireKeystore = System.getenv("GRIMOIRE_KEYSTORE")
    val grimoireKeystorePass = System.getenv("GRIMOIRE_KEYSTORE_PASS")
    if (grimoireKeystore != null && grimoireKeystorePass != null) {
        signingConfigs {
            create("grimoire") {
                storeFile = file(grimoireKeystore)
                storePassword = grimoireKeystorePass.trim()
                keyAlias = "grimoire"
                keyPassword = grimoireKeystorePass.trim()
            }
        }
    }

    buildTypes {
        debug {
            if (grimoireKeystore != null && grimoireKeystorePass != null) {
                signingConfig = signingConfigs.getByName("grimoire")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":engine"))

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.datastore:datastore:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.8")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
