// Карточные игры для незрячих.
//
// engine — чистая логика игр (без Android), тестируется отдельно.
// app    — Android-приложение поверх движка.
plugins {
    kotlin("jvm") version "2.2.21" apply false
    kotlin("android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("com.android.application") version "8.13.2" apply false
}

subprojects {
    repositories {
        google()
        mavenCentral()
    }
}
