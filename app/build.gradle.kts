plugins {
    id("com.android.application")
}

android {
    namespace = "com.attdes.mcx"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.attdes.mcx"
        minSdk = 23
        targetSdk = 36
        versionCode = 10
        versionName = "1.0-beta1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.documentfile:documentfile:1.1.0")

    // ZIP/7Z and other archive formats.
    implementation("org.apache.commons:commons-compress:1.28.0")
    // RAR extraction, including password-protected RAR.
    implementation("com.github.junrar:junrar:7.6.0")

}
