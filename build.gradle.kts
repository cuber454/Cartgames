// Карточные игры для незрячих. Пока в сборке только движок (engine):
// это чистая логика, её можно тестировать без телефона и Android SDK.
plugins {
    kotlin("jvm") version "2.1.0" apply false
}

subprojects {
    repositories {
        mavenCentral()
    }
}
