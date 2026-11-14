import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// API keys live in local.properties, which is git-ignored, so they never reach GitHub.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun localProp(vararg names: String, default: String = ""): String =
    names.firstNotNullOfOrNull { localProps.getProperty(it)?.trim()?.takeIf(String::isNotEmpty) } ?: default

android {
    namespace = "com.example.sagana"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.sagana"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GEOAPIFY_KEY", "\"${localProp("GEOAPIFY_API_KEY", "GEOAPIFY_KEY")}\"")
        buildConfigField("String", "PHILSMS_TOKEN", "\"${localProp("PHILSMS_TOKEN")}\"")
        buildConfigField("String", "PHILSMS_SENDER_ID", "\"${localProp("PHILSMS_SENDER_ID", default = "PhilSMS")}\"")
        // Tokens made on the new PhilSMS dashboard only work on this host (app.philsms.com rejects them).
        buildConfigField("String", "PHILSMS_BASE_URL",
            "\"${localProp("PHILSMS_BASE_URL", default = "https://dashboard.philsms.com/api/v3").trimEnd('/')}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.mpandroidchart)
    implementation(libs.glide)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.camerax.mlkit)
    implementation(libs.mlkit.barcode)
    implementation(libs.zxing.core)
    implementation(libs.osmdroid)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
