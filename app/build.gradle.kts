plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "com.enstars.autoplayer"
 compileSdk = 35
 val keystorePath = System.getenv("ENSTARS_KEYSTORE_PATH")
 val keystorePassword = System.getenv("ENSTARS_KEYSTORE_PASSWORD")
 val keyAlias = System.getenv("ENSTARS_KEY_ALIAS")
 val keyPassword = System.getenv("ENSTARS_KEY_PASSWORD")
 val hasSigning = listOf(keystorePath,keystorePassword,keyAlias,keyPassword).all { !it.isNullOrBlank() }
 defaultConfig {
  applicationId = "com.enstars.autoplayer"
  minSdk = 30
  targetSdk = 32
  versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1) + 100
  versionName = "3.6." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
 }
 if(hasSigning) {
  signingConfigs {
   create("upgrade") {
    storeFile = file(keystorePath!!)
    storePassword = keystorePassword
    this.keyAlias = keyAlias
    this.keyPassword = keyPassword
   }
  }
 }
 buildTypes {
  getByName("release") {
   isMinifyEnabled = false
   if(hasSigning) signingConfig = signingConfigs.getByName("upgrade")
  }
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}

dependencies {
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.json:json:20240303")
}
