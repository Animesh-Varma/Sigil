plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // If you are on a newer Android Studio, use this alias, otherwise keep the one you had
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.animeshvarma.sigil"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.animeshvarma.sigil"
        minSdk = 26
        // Update targetSdk to match compileSdk to avoid compatibility behaviors
        //noinspection EditedTargetSdkVersion
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        // This version must match your Kotlin version.
        // If using Kotlin 1.9.0+, use "1.5.1". If using Kotlin 2.0+, this block isn't needed.
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose BOM (Bill of Materials) - Latest Stable
    implementation(platform("androidx.compose:compose-bom:2024.11.00"))

    // Core UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material3:material3:1.4.0")


    // Logic
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Crypto
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.79")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation(libs.androidx.benchmark.common)

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.11.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}