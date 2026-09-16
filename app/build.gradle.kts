import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val local = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun setting(name: String, fallback: String = ""): String =
    providers.gradleProperty(name).orNull ?: System.getenv(name) ?: local.getProperty(name, fallback)
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.getevapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.getevapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "2.0.0-native"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", quoted(setting("API_BASE_URL", "http://158.180.67.53:8001/api/")))
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", quoted(setting("KAKAO_NATIVE_APP_KEY")))
        buildConfigField("String", "TERMS_URL", quoted(setting("TERMS_URL")))
        buildConfigField("String", "PRIVACY_URL", quoted(setting("PRIVACY_URL")))
        manifestPlaceholders["kakaoScheme"] = "kakao" + setting("KAKAO_NATIVE_APP_KEY", "unconfigured")
    }
    signingConfigs.getByName("debug") {
        setting("DEBUG_KEYSTORE").takeIf { it.isNotBlank() }?.let { storeFile = rootProject.file(it) }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.08.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.kakao.sdk:v2-user:2.20.6")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    // Android 17 removed the reflective InputManager API used by Espresso 3.6.
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
