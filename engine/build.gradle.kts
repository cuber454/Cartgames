plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()

    // Прогон бота против бота идёт тысячей матчей — так записано в KOZEL.md,
    // 4.6. Для быстрой прикидки при подборе весов серию можно укоротить:
    // ./gradlew :engine:test -Dkozel.sim.matches=50
    listOf("kozel.sim.matches").forEach { name ->
        System.getProperty(name)?.let { systemProperty(name, it) }
    }
}
