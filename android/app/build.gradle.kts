import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

// ── Read secrets from local.properties (dev) or environment variables (CI) ───
// Never commit local.properties to git.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

fun secret(key: String): String {
    // CI injects as environment variable; local dev uses local.properties
    return System.getenv(key)
        ?: localProps.getProperty(key)
        ?: error("Missing required secret: $key — add to local.properties or CI environment")
}

android {
    namespace = "com.pixelmentor.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pixelmentor.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.1"

        buildConfigField("String", "ENTRA_CLIENT_ID", "\"51c1a8ba-2b07-4d99-bd91-4652081f7b41\"")
        buildConfigField("String", "ENTRA_TENANT_ID", "\"260b8d50-600d-47d4-b73c-e094c1674813\"")
        buildConfigField("String", "API_BASE_URL_DEV", "\"https://pixelmentor-production.up.railway.app/\"")
        buildConfigField("String", "API_BASE_URL_PROD", "\"https://pm-prod-api.happyfield-58cc0921.australiaeast.azurecontainerapps.io/\"")
        // Supabase URL is not sensitive — it's visible in network traffic
        buildConfigField("String", "SUPABASE_URL", "\"https://sevikgqyffziljftqabd.supabase.co\"")
        // Secrets — injected from local.properties (dev) or CI environment variables (CI/CD)
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${secret("SUPABASE_ANON_KEY")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${secret("GOOGLE_WEB_CLIENT_ID")}\"")
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
    implementation("io.ktor:ktor-client-android:3.1.2")

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
