plugins { 
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.esempio.analisiemesso"
    compileSdk = 34
    defaultConfig { 
        applicationId = "com.esempio.analisiemesso"
        minSdk = 26
        targetSdk = 34
    }
}

// Forza Gradle ad accettare le licenze durante la sincronizzazione
System.setProperty("android.sdk.licenses.accepted", "true")

dependencies {
    implementation("com.google.firebase:firebase-auth-ktx:22.3.1")
    implementation("com.google.firebase:firebase-database-ktx:20.3.1")
}
