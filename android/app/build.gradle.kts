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
    return System.getenv(key)
        ?: localProps.getProperty(key)
        ?: error("Missing required secret: $key — add to local.properties or CI environment")
}

android {
    namespace = "com.pixelmentor.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pixelmentor.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 22
        versionName = "1.2.2"

        buildConfigField("String", "ENTRA_CLIENT_ID", "\"51c1a8ba-2b07-4d99-bd91-4652081f7b41\"")
        buildConfigField("String", "ENTRA_TENANT_ID", "\"260b8d50-600d-47d4-b73c-e094c1674813\"")
        buildConfigField("String", "API_BASE_URL_DEV", "\"https://pixelmentor-production.up.railway.app/\"")
        buildConfigField("String", "API_BASE_URL_PROD", "\"https://pixelmentor-production-5e4b.up.railway.app/\"")
        buildConfigField("String", "SUPABASE_URL", "\"https://sevikgqyffziljftqabd.supabase.co\"")
        buildConfigField("String", "SUPABASE_ANON_KEY",     "\"${secret("SUPABASE_ANON_KEY")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID",  "\"${secret("GOOGLE_WEB_CLIENT_ID")}\"")
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

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    // ── Compose BOM (May 2026 — Compose 1.11.x) ───────────────────────────────
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    // ── Image loading ─────────────────────────────────────────────────────────
    implementation("io.coil-kt:coil-compose:2.7.0")

    // ── Permissions ───────────────────────────────────────────────────────────
    // Accompanist 0.37.0 — aligned with Compose 1.11.x
    implementation("com.google.accompanist:accompanist-permissions:0.37.0")

    // ── Hilt navigation ───────────────────────────────────────────────────────
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    // ── Activity + Navigation ─────────────────────────────────────────────────
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // ── Hilt ──────────────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    // kotlin-metadata-jvm workaround: Hilt's annotation processor needs this
    // to read Kotlin 2.3.x class metadata when running under AGP 8.x.
    // Without it hiltJavaCompileDebug fails with "maximum supported version is 2.2.0".
    ksp("org.jetbrains.kotlin:kotlin-metadata-jvm:2.3.21")

    // ── Retrofit 3.0.0 + OkHttp 4.12.0 ──────────────────────────────────────
    // Retrofit 3.x requires OkHttp 4.12+ (written in Kotlin); binary-compatible
    // with 2.x so no interface changes needed in existing repository code.
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // ── Supabase 3.3.0 + Ktor 3.4.0 ─────────────────────────────────────────
    // supabase-kt 3.3.0 ships with Ktor 3.4.0 as its recommended engine version
    implementation(platform("io.github.jan-tennert.supabase:bom:3.3.0"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.ktor:ktor-client-android:3.4.0")

    // ── DataStore ────────────────────────────────────────────────────────────
    implementation(libs.datastore.preferences)

    // ── Coroutines 1.10.2 ────────────────────────────────────────────────────
    implementation(libs.coroutines.android)

    // ── Testing ───────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
}