import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Kunci API opsional (Last.fm). Isi di local.properties, kosongkan jika tidak dipakai.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.veruproject.vmusix"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.veruproject.vmusix"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "LASTFM_API_KEY", "\"${localProps.getProperty("LASTFM_API_KEY", "")}\"")
        buildConfigField("String", "LASTFM_API_SECRET", "\"${localProps.getProperty("LASTFM_API_SECRET", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            // ponytail: tanda tangan debug supaya APK release langsung ter-install tanpa keystore.
            // Untuk rilis Play Store, ganti dengan keystore milik VeruProject.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    // Ikon aplikasi digenerate dari assets/app_icon.png (lihat README: Cara Mengganti Ikon).
    sourceSets.getByName("main").res.srcDir(layout.buildDirectory.dir("generated/appIcon/res"))
}

val generateAppIcon = tasks.register("generateAppIcon") {
    val src = layout.projectDirectory.file("src/main/assets/app_icon.png")
    val outDir = layout.buildDirectory.dir("generated/appIcon/res")
    inputs.file(src)
    outputs.dir(outDir)
    doLast {
        val out = outDir.get().asFile
        listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi").forEach { density ->
            val dir = File(out, "mipmap-$density").apply { mkdirs() }
            src.asFile.copyTo(File(dir, "ic_launcher.png"), overwrite = true)
        }
    }
}

tasks.named("preBuild") {
    dependsOn(generateAppIcon)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    implementation(libs.coroutines.android)
}
