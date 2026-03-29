plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
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
        buildConfigField("String", "API_BASE_URL_DEV", "\"https://pm-dev-api.happymushroom-b6080bcd.australiaeast.azurecontainerapps.io/\"")
        buildConfigField("String", "API_BASE_URL_PROD", "\"https://pm-prod-api.happyfield-58cc0921.australiaeast.azurecontainerapps.io/\"")

        manifestPlaceholders["msalRedirectScheme"] = "msauth"
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

    // Force resolution of the display-mask dependency that MSAL 5.x pulls in.
    // The artifact exists in the Azure DevOps feed but under a different version.
    configurations.all {
        resolutionStrategy {
            force("com.microsoft.device.display:display-mask:0.3.0")
            // If 0.3.0 still can't resolve, substitute with a no-op empty module
            dependencySubstitution {
                substitute(module("com.microsoft.device.display:display-mask"))
                    .using(module("com.microsoft.device.display:display-mask:0.3.0"))
            }
        }
    }
}

dependencies {
    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
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

    // MSAL — exclude the display-mask dependency that can't be resolved
    // from public repos. It's only needed for Surface Duo foldable support,
    // which PixelMentor doesn't use.
    implementation(libs.msal) {
        exclude(group = "com.microsoft.device.display", module = "display-mask")
    }

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
