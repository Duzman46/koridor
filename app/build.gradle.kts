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

val appProperties = Properties().apply {
    val configuration = rootProject.file("app.properties")
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

fun appValue(name: String): String =
    (appProperties.getProperty(name) ?: System.getenv(name) ?: "")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

// App Check ships switched off. Turning it on in the app before Play Integrity is registered
// and enforcement is enabled in the Firebase console would lock every device out of the
// backend, so this stays a deliberate, one-line opt-in. See docs/FIREBASE_SETUP.md.
val appCheckEnabled = firebaseValue("GRIDBOUND_APP_CHECK_ENABLED").equals("true", ignoreCase = true)

val testAdMobAppId = "ca-app-pub-3940256099942544~3347511713"
val testBannerAdUnitId = "ca-app-pub-3940256099942544/9214589741"
val testInterstitialAdUnitId = "ca-app-pub-3940256099942544/1033173712"
val configuredAdMobAppId = monetizationValue("KORIDOR_ADMOB_APP_ID")
val configuredBannerAdUnitId = monetizationValue("KORIDOR_ADMOB_BANNER_ID")
val configuredInterstitialAdUnitId = monetizationValue("KORIDOR_ADMOB_INTERSTITIAL_ID")
val premiumProductId = monetizationValue("KORIDOR_PREMIUM_PRODUCT_ID", "remove_ads")

// Cosmetic products. An id left empty means the product is not set up in the Play Console
// yet, and the app then hides it rather than offering a purchase Play would reject.
val themeMidnightProductId = monetizationValue("KORIDOR_THEME_MIDNIGHT_PRODUCT_ID")
val themeSunsetProductId = monetizationValue("KORIDOR_THEME_SUNSET_PRODUCT_ID")
val monetizationConfigured = configuredAdMobAppId.isNotBlank() &&
    configuredBannerAdUnitId.isNotBlank() &&
    configuredInterstitialAdUnitId.isNotBlank()

android {
    namespace = "com.duzman46.gridbound"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.duzman46.gridbound"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "0.4.0"

        buildConfigField("String", "FIREBASE_API_KEY", "\"${firebaseValue("GRIDBOUND_FIREBASE_API_KEY")}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${firebaseValue("GRIDBOUND_FIREBASE_APP_ID")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${firebaseValue("GRIDBOUND_FIREBASE_PROJECT_ID")}\"")
        buildConfigField("String", "FIREBASE_DATABASE_URL", "\"${firebaseValue("GRIDBOUND_FIREBASE_DATABASE_URL")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${firebaseValue("GRIDBOUND_GOOGLE_WEB_CLIENT_ID")}\"")
        buildConfigField("boolean", "APP_CHECK_ENABLED", appCheckEnabled.toString())
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"${appValue("KORIDOR_PRIVACY_POLICY_URL")}\"")
        buildConfigField("String", "TERMS_URL", "\"${appValue("KORIDOR_TERMS_URL")}\"")
        buildConfigField("String", "PREMIUM_PRODUCT_ID", "\"$premiumProductId\"")
        buildConfigField("String", "THEME_MIDNIGHT_PRODUCT_ID", "\"$themeMidnightProductId\"")
        buildConfigField("String", "THEME_SUNSET_PRODUCT_ID", "\"$themeSunsetProductId\"")
        buildConfigField("boolean", "MONETIZATION_CONFIGURED", monetizationConfigured.toString())
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        // The ten shipped languages. Anything else is stripped from the bundle, and a device
        // set to an unlisted language falls back to the base (English) resources.
        // Keep in step with res/xml/locales_config.xml.
        localeFilters += listOf("en", "tr", "es", "pt-rBR", "de", "fr", "ru", "ar", "in", "hi")
        // locales_config.xml is maintained by hand so it can carry comments and stay in
        // step with the AppLanguage enum; AGP must not overwrite it.
        generateLocaleConfig = false
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
            // Google's own test identifiers, all the way down to the app id. A development
            // build must be incapable of reaching the real AdMob account: an accidental
            // impression or self-click on a live unit is what gets an account suspended.
            manifestPlaceholders["ADMOB_APP_ID"] = testAdMobAppId
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$testBannerAdUnitId\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$testInterstitialAdUnitId\"")
            // Adds en-XA (accented, ~1.4x longer text) and en-XB (right-to-left) so text
            // overflow and RTL mirroring can be checked on any device without a translation.
            isPseudoLocalesEnabled = true
        }
        release {
            // Falls back to the test app id when monetization.properties is missing, so an
            // unconfigured release build is inert rather than pointing at nothing.
            manifestPlaceholders["ADMOB_APP_ID"] = configuredAdMobAppId.ifBlank { testAdMobAppId }
            buildConfigField("String", "ADMOB_BANNER_ID", "\"${configuredBannerAdUnitId.ifBlank { testBannerAdUnitId }}\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"${configuredInterstitialAdUnitId.ifBlank { testInterstitialAdUnitId }}\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    bundle {
        language {
            // All ten languages ship in the base APK. Play's per-language split would only
            // install the device language, and the in-app language picker would then have
            // nothing to switch to — the app is not using Play Feature Delivery to fetch
            // languages on demand. Costs ~100 KB; a language picker that silently fails is
            // not worth saving it.
            enableSplit = false
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
    implementation(libs.firebase.appcheck.playintegrity)
    // Debug-only: lets a development build register a token with the console instead of
    // being rejected. Never reaches a release APK.
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)
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
