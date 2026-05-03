import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
}

val releaseVersionCodeProvider = providers.gradleProperty("releaseVersionCode").orElse("1")
val releaseVersionNameProvider = providers.gradleProperty("releaseVersionName").orElse("0.1.0")
val releaseKeystorePathProvider = providers.environmentVariable("ANDROID_KEYSTORE_PATH")
val releaseKeystoreBase64Provider = providers.environmentVariable("ANDROID_KEYSTORE_BASE64")
val releaseKeystorePasswordProvider = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAliasProvider = providers.environmentVariable("ANDROID_KEY_ALIAS")
val releaseKeyPasswordProvider = providers.environmentVariable("ANDROID_KEY_PASSWORD")

fun decodeReleaseKeystore(): File? {
    val explicitPath = releaseKeystorePathProvider.orNull
    if (!explicitPath.isNullOrBlank()) {
        return file(explicitPath)
    }

    val base64Value = releaseKeystoreBase64Provider.orNull ?: return null
    val outputFile = layout.buildDirectory.file("generated/signing/release-keystore.jks").get().asFile

    if (!outputFile.exists()) {
        outputFile.parentFile.mkdirs()
        outputFile.writeBytes(Base64.getDecoder().decode(base64Value))
    }

    return outputFile
}

val releaseKeystoreFile = decodeReleaseKeystore()
val hasReleaseSigning = releaseKeystoreFile != null &&
    !releaseKeystorePasswordProvider.orNull.isNullOrBlank() &&
    !releaseKeyAliasProvider.orNull.isNullOrBlank() &&
    !releaseKeyPasswordProvider.orNull.isNullOrBlank()

android {
    namespace = "com.kraat.lostfilmnewtv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kraat.lostfilmnewtv"
        minSdk = 26
        targetSdk = 35
        versionCode = releaseVersionCodeProvider.get().toInt()
        versionName = releaseVersionNameProvider.get()
        testInstrumentationRunner = "com.kraat.lostfilmnewtv.HiltTestRunner"
        buildConfigField("String", "TMDB_API_KEY", "\"eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI0YzFlMmNlZDkxZGQxOTAwMTg0MTZiNzQwNzgzMDgwNCIsIm5iZiI6MTcwNDEwODE4My4wODgsInN1YiI6IjY1OTJhMDk3NTcxNzZmNmJjYjdmOTk2YSIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ.vsBAhcyuC-XB5b1Um8B8Ro2Fcg58QQV6YBRd4XdZyOs\"")
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = releaseKeystoreFile
                storePassword = releaseKeystorePasswordProvider.orNull
                keyAlias = releaseKeyAliasProvider.orNull
                keyPassword = releaseKeyPasswordProvider.orNull
            }
        }
    }

    buildTypes {
        release {
            // Enable release shrinking to reduce APK size and strip unused bytecode/resources.
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        animationsDisabled = true
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.tvprovider)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.jsoup)
    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.core)
    kapt(libs.androidx.room.compiler)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    kapt(libs.hilt.android.compiler)
    implementation(libs.hilt.work)
    kapt(libs.hilt.work.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit4)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.hilt.android.testing)
    kaptTest(libs.hilt.android.compiler)
    testImplementation(libs.mockito.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kaptAndroidTest(libs.hilt.android.compiler)
}
