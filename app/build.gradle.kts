import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val firebaseProperties = Properties().apply {
    val configuration = rootProject.file("firebase.properties")
    if (configuration.exists()) configuration.inputStream().use(::load)
}

val monetizationProperties = Properties().apply {
    val configuration = rootProject.file("monetization.properties")
    if (configuration.exists()) configuration.inputStream().use(::load)
}

val keystoreProperties = Properties().apply {
    val configuration = rootProject.file("keystore.properties")
    if (configuration.exists()) configuration.inputStream().use(::load)
}
val releaseStoreFile = keystoreProperties.getProperty("storeFile").orEmpty()
val releaseSigningConfigured = releaseStoreFile.isNotBlank() &&
    keystoreProperties.getProperty("storePassword").orEmpty().isNotBlank() &&
    keystoreProperties.getProperty("keyAlias").orEmpty().isNotBlank() &&
    keystoreProperties.getProperty("keyPassword").orEmpty().isNotBlank()

fun firebaseValue(name: String): String =
    (firebaseProperties.getProperty(name) ?: System.getenv(name) ?: "")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

fun monetizationValue(name: String, fallback: String = ""): String =
    (monetizationProperties.getProperty(name) ?: System.getenv(name) ?: fallback)
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

val testAdMobAppId = "ca-app-pub-3940256099942544~3347511713"
val testBannerAdUnitId = "ca-app-pub-3940256099942544/9214589741"
val configuredAdMobAppId = monetizationValue("KORIDOR_ADMOB_APP_ID")
val configuredBannerAdUnitId = monetizationValue("KORIDOR_ADMOB_BANNER_ID")
val premiumProductId = monetizationValue("KORIDOR_PREMIUM_PRODUCT_ID", "remove_ads")
val monetizationConfigured = configuredAdMobAppId.isNotBlank() && configuredBannerAdUnitId.isNotBlank()

android {
    namespace = "com.duzman46.gridbound"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.duzman46.gridbound"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "0.3.0"

        buildConfigField("String", "FIREBASE_API_KEY", "\"${firebaseValue("GRIDBOUND_FIREBASE_API_KEY")}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${firebaseValue("GRIDBOUND_FIREBASE_APP_ID")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${firebaseValue("GRIDBOUND_FIREBASE_PROJECT_ID")}\"")
        buildConfigField("String", "FIREBASE_DATABASE_URL", "\"${firebaseValue("GRIDBOUND_FIREBASE_DATABASE_URL")}\"")
        buildConfigField("String", "PREMIUM_PRODUCT_ID", "\"$premiumProductId\"")
        buildConfigField("boolean", "MONETIZATION_CONFIGURED", monetizationConfigured.toString())
        manifestPlaceholders["ADMOB_APP_ID"] = configuredAdMobAppId.ifBlank { testAdMobAppId }
        resourceConfigurations += listOf("en", "tr")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$testBannerAdUnitId\"")
        }
        release {
            buildConfigField("String", "ADMOB_BANNER_ID", "\"${configuredBannerAdUnitId.ifBlank { testBannerAdUnitId }}\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = false
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    androidTestImplementation(platform(libs.compose.bom))

    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.play.billing)
    implementation(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    ksp(libs.hilt.compiler)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
}
