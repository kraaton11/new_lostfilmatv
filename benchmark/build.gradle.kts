plugins {
    id("com.android.test")
    // Плагин androidx.baselineprofile не обязателен — тест создаст .prof-файл напрямую
}

android {
    namespace = "com.kraat.lostfilmnewtv.benchmark"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.test.uiautomator)
}
