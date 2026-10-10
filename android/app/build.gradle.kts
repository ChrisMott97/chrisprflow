import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The default base URL can be overridden in local.properties, which git ignores.
// The token is not built in: it is entered on the settings screen and stored encrypted.
val local = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "app.dictation"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.dictation"
        minSdk = 34
        targetSdk = 36
        versionCode = 2
        versionName = "d2"
        val baseUrl = local.getProperty("dictation.baseUrl", "https://dictation.chrisamott97.workers.dev")
        buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Silero VAD (ONNX model bundled, runs on onnxruntime-android).
    implementation("com.github.gkonovalov.android-vad:silero:2.0.10")
    testImplementation("junit:junit:4.13.2")
}
