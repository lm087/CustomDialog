plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.mt.customDialog"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.mt.customDialog"
        minSdk = 23
        targetSdk = 37
        versionCode = 8
        versionName = "1.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    bundle {
        language {
            enableSplit = false
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
