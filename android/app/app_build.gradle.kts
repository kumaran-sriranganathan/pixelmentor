plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.pixelmentor.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pixelmentor.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "ENTRA_CLIENT_ID", "\"51c1a8ba-2b07-4d99-bd91-4652081f7b41\"")
        buildConfigField("String", "ENTRA_TENANT_ID", "\"260b8d50-600d-47d4-b73c-e094c1674813\"")
        buildConfigField("String", "API_BASE_URL_DEV", "\"https://pixelmentor-production.up.railway.app/\"")
        buildConfigField("String", "API_BASE_URL_PROD", "\"https://pm-prod-api.happyfield-58cc0921.australiaeast.azurecontainerapps.io/\"")
        buildConfigField("String", "SUPABASE_URL", "\"https://sevikgqyffziljftqabd.supabase.co\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InNldmlrZ3F5ZmZ6aWxqZnRxYWJkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzUwOTMwODcsImV4cCI6MjA5MDY2OTA4N30.fY4Xsb49ieK8SnUYoulk_MJVfKRz8XKlnZtc9olWCNE\"")
        // Replace YOUR_WEB_CLIENT_ID with the Web Client ID from Google Cloud Console
        // It looks like: 123456789-abcdefgh.apps.googleusercontent.com
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"YOUR_WEB_CLIENT_ID\"")
    }

    buildTypes {
        debug {
            buildConfigField("Boolean", "USE_DEV_API", "true")
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            buildConfigField("Boolean", "USE_DEV_API", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    debugImplementation(libs.compose.ui.tooling)

    // Activity + Navigation
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Retrofit + OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(platform("io.github.jan-tennert.supabase:bom:3.1.4"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:compose-auth")
    implementation("io.ktor:ktor-client-android:3.1.2")

    // Google Sign-In via Credential Manager
    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.googleid)

    // DataStore
    implementation(libs.datastore.preferences)

    // Coroutines
    implementation(libs.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
}
