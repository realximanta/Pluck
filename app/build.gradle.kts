plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "xyz.ximanta.pluck"
    compileSdk = 35

    defaultConfig {
        applicationId = "xyz.ximanta.pluck"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "2.0.2"

        vectorDrawables { useSupportLibrary = true }

        val rawApiBaseUrl = (project.findProperty("PLUCK_API_BASE_URL") as? String)?.trim()
        val apiBaseUrl = if (rawApiBaseUrl.isNullOrEmpty()) "https://pluck.ximanta.xyz" else rawApiBaseUrl
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    signingConfigs {
        val ksPath = System.getenv("KEYSTORE_PATH")
        val ksPassword = System.getenv("KEYSTORE_PASSWORD")
        val keyAliasEnv = System.getenv("KEY_ALIAS")
        val keyPasswordEnv = System.getenv("KEY_PASSWORD")

        if (ksPath != null && ksPath.isNotBlank() &&
            ksPassword != null && ksPassword.isNotBlank() &&
            keyAliasEnv != null && keyAliasEnv.isNotBlank() &&
            keyPasswordEnv != null && keyPasswordEnv.isNotBlank()
        ) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = ksPassword
                keyAlias = keyAliasEnv
                this.keyPassword = keyPasswordEnv
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Added Coil for cover images
    implementation("io.coil-kt:coil-compose:2.7.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
