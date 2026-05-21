plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

fun String.asBuildConfigString(): String = replace("\\", "\\\\").replace("\"", "\\\"")

val defaultRegistryUrl = "https://raw.githubusercontent.com/Anezium/RokidBrew-Registry/main/dist/apps.v1.json"
val registryUrl = providers.gradleProperty("rokidbrewRegistryUrl").orElse(defaultRegistryUrl).get()

android {
    namespace = "com.rokidbrew.phone"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.rokidbrew.phone"
        minSdk = 28
        targetSdk = 36
        versionCode = 8
        versionName = "0.1.7"
        buildConfigField("String", "ROKIDBREW_REGISTRY_URL", "\"${registryUrl.asBuildConfigString()}\"")
        manifestPlaceholders["cleartextTrafficPermitted"] = "false"
    }

    buildTypes {
        debug {
            manifestPlaceholders["cleartextTrafficPermitted"] = "true"
        }

        release {
            isMinifyEnabled = false
            manifestPlaceholders["cleartextTrafficPermitted"] = "false"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation(files("libs/client-l-1.0.1.aar"))

    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation(libs.androidx.compose.ui.tooling)
}
