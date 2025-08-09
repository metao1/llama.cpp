plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.metao.ai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.metao.ai"
        minSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // START OF FIX 1: Replace deprecated kotlinOptions with the new 'kotlin' block
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    // END OF FIX 1
    buildFeatures {
        compose = true
    }
    composeOptions {
        // Version compatible with Kotlin 1.9.20
        kotlinCompilerExtensionVersion = "1.5.4"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Configure Kotlin compilation
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        // Ensure this matches your project's JVM target
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)

        // Add recommended Kotlin compiler arguments
        freeCompilerArgs.addAll(
            "-Xjvm-default=all" // Recommended for modern Kotlin
        )
    }
}

configurations.all {
    resolutionStrategy {
        // Force specific versions to avoid version conflicts
        force("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.20")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.20")
        force("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
        force("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    }
}

dependencies {
    implementation(project(":llama"))

    val composeBom = platform("androidx.compose:compose-bom:2025.07.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Material Design 3
    implementation("androidx.compose.material3:material3")

    // Android Studio Preview support
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.compose.material:material-icons-core")
    // Optional - Add full set of material icons
    implementation("androidx.compose.material:material-icons-extended")
    // Optional - Add window size utils
    implementation("androidx.compose.material3.adaptive:adaptive")
    implementation("androidx.compose.material3:material3-window-size-class")

    // Optional - Integration with activities
    implementation("androidx.activity:activity-compose")
    // Optional - Integration with ViewModels

    // Optional - Integration with LiveData
    implementation("androidx.compose.runtime:runtime-livedata")
    // Optional - Integration with RxJava
    implementation("androidx.compose.runtime:runtime-rxjava2")

    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")

    // Koin for dependency injection
    implementation("io.insert-koin:koin-android:3.5.0")
    implementation("io.insert-koin:koin-androidx-compose:3.5.0")

    // Room database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

}

// This block ensures JUnit Platform is used for tests
tasks.withType<Test> {
    useJUnitPlatform()
}
