plugins {
    alias(libs.plugins.androidApplication)
}

android {
    namespace = "com.example.productprice"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.productprice"
        minSdk = 23
        targetSdk = 34
        versionCode = 3
        versionName = "3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            providers.gradleProperty("officialFixtureDir").orNull?.let { dir -> test.systemProperty("official.fixture.dir", dir) }
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)

    // Smart official company PDF price-list import.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Local app-lock biometric unlock.
    implementation("androidx.biometric:biometric:1.1.0")

    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("org.apache.pdfbox:pdfbox:2.0.27")
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
