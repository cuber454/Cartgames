plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "games.cardgames"
    compileSdk = 36

    defaultConfig {
        applicationId = "games.cardgames"
        minSdk = 24
        targetSdk = 36
        // Номер сборки приходит из CI (-PversionCode=…): он растёт с каждым
        // прогоном, и по нему приложение понимает, что вышла новая версия.
        // Сборка без параметра (локальная, студийная) берёт запасное число.
        versionCode = (findProperty("versionCode") as String?)?.toIntOrNull() ?: 18
        versionName = (findProperty("versionName") as String?) ?: "0.9"
    }

    signingConfigs {
        // Ключ подписи лежит в секретах репозитория и появляется здесь только
        // на раннере CI. Без него release-сборка не собирается, а debug-подпись
        // не годится: она новая на каждом прогоне, и обновление поверх уже
        // установленного приложения Android не примет — только снос с потерей
        // партий и журнала.
        create("release") {
            val storePath = System.getenv("KEYSTORE_FILE")
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Подпись подставляем, только если ключ на месте: иначе сборка
            // падала бы на ровном месте при локальном прогоне без секретов.
            signingConfigs.findByName("release")
                ?.takeIf { it.storeFile != null }
                ?.let { signingConfig = it }
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":engine"))

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.activity:activity-compose:1.12.4")

    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    // Тесты приложения — про разбор файла обновления: его пишет CI, читает
    // приложение, и разойтись они могут молча (см. UpdateTest).
    testImplementation("junit:junit:4.13.2")
}
