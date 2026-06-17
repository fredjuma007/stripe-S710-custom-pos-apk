import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val rafikiPayBackendUrl = providers.gradleProperty("RAFIKIPAY_BACKEND_URL").orElse("https://example.invalid")
val rafikiPayDeviceToken = providers.gradleProperty("RAFIKIPAY_DEVICE_TOKEN").orElse("dev-device-token")
val rafikiPayTerminalLocationId = providers.gradleProperty("RAFIKIPAY_TERMINAL_LOCATION_ID").orElse("")

android {
    namespace = "community.rafiki.pay"
    compileSdk = 35

    defaultConfig {
        applicationId = "community.rafiki.pay"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BACKEND_BASE_URL", "\"${rafikiPayBackendUrl.get()}\"")
        buildConfigField("String", "DEVICE_TOKEN", "\"${rafikiPayDeviceToken.get()}\"")
        buildConfigField("String", "TERMINAL_LOCATION_ID", "\"${rafikiPayTerminalLocationId.get()}\"")
    }

    signingConfigs {
        create("releaseEnv") {
            val storeFilePath = providers.gradleProperty("RAFIKIPAY_KEYSTORE_FILE").orNull
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                storePassword = providers.gradleProperty("RAFIKIPAY_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.gradleProperty("RAFIKIPAY_KEY_ALIAS").orNull
                keyPassword = providers.gradleProperty("RAFIKIPAY_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-test"
            buildConfigField("Boolean", "SIMULATED_READER", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("releaseEnv")
            buildConfigField("Boolean", "SIMULATED_READER", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
        buildConfig = true
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

android.applicationVariants.all {
    outputs.all {
        val suffix = if (buildType.name == "release") "" else "-$name"
        (this as BaseVariantOutputImpl).outputFileName = "RafikiPay-v$versionName$suffix.apk"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.stripe:stripeterminal-core:5.6.0")
    implementation("com.stripe:stripeterminal-appsondevices:5.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
