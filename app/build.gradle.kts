plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "com.enstars.autoplayer"
 compileSdk = 35
 defaultConfig { applicationId = "com.enstars.autoplayer"; minSdk = 30; targetSdk = 32; versionCode = 1; versionName = "1.0" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}