import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("dev.mantikafasi.manticord.plugin")
}

val releaseSigningFile = rootProject.file("keystore.properties")
val releaseSigning = Properties().apply {
    if (releaseSigningFile.exists()) releaseSigningFile.inputStream().use(::load)
}

manticordPlugin {
    id.set("io.github.mmaarij123.v2componentfix")
    entrypoint.set("io.github.mmaarij123.v2componentfix.V2ComponentFixPlugin")
    description.set("Fits Components V2 cards, plays animated media, and repairs missing sections and media")
    manticordVersionMinimum.set(3)
    manticordVersionMaximumExclusive.set(4)
    async()
}

android {
    namespace = "io.github.mmaarij123.v2componentfix"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "io.github.mmaarij123.v2componentfix"
        minSdk = 23
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.0"
    }

    signingConfigs {
        if (releaseSigningFile.exists()) create("release") {
            storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
            storePassword = releaseSigning.getProperty("storePassword")
            keyAlias = releaseSigning.getProperty("keyAlias")
            keyPassword = releaseSigning.getProperty("keyPassword")
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    val localPluginApi = file("../manticordrebuild/build/plugin-sdk/manticord-plugin-api-1.0.6.jar")
    val pluginApi =
        if (localPluginApi.exists()) files(localPluginApi)
        else "dev.mantikafasi.manticord:plugin-api:1.0.6"

    compileOnly(pluginApi)
    compileOnly(libs.rxjava)
    compileOnly(libs.recyclerview)
    compileOnly(libs.viewbinding)
    compileOnly(libs.fresco.vito.view)
    compileOnly(libs.fresco.vito.core)
    testImplementation(pluginApi)
    testImplementation(libs.junit)
}
