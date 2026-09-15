pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "card-games"

// Движок — чистая логика игр, тестируется без Android.
include("engine")

// Android-приложение: экран, озвучка, жесты, настройки.
include("app")
