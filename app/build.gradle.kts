plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.myfuturecode.mfcdash"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.myfuturecode.mfcdash"
        minSdk = 24
        targetSdk = 35
        versionCode = 15
        versionName = "1.7.3"
        // Dashboard ka address (aakhri slash ke saath). App ke andar Settings se bhi badal sakte ho.
        buildConfigField("String", "DEFAULT_BASE_URL", "\"https://tirangae-online.live/mfc_dash/\"")
    }

    // Release key GitHub secret se aati hai (CI keystore file ka path + passwords env me deta hai). Secret na ho to
    // release APK debug key se sign hota hai (Sagar ne 2026-10-22 ko yahi chuna, jab tak apni key ka secret nahi daalte).
    val ksFile = System.getenv("MFC_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (ksFile != null) create("release") {
            storeFile = file(ksFile)
            storePassword = System.getenv("MFC_KEYSTORE_PASS")
            keyAlias = System.getenv("MFC_KEY_ALIAS")
            keyPassword = System.getenv("MFC_KEY_PASS")
        }
    }
    buildTypes {
        // Sagar ko release APK jaata hai: debuggable nahi, isliye ART poori speed se chalata hai (debug APK se kaafi smooth).
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (ksFile != null) "release" else "debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // home-screen widgets
    implementation("androidx.glance:glance-appwidget:1.1.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
