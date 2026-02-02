plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    alias(libs.plugins.kover)
    application
}

group = "com.moatbot"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    // Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Telegram (tgbotapi)
    implementation(libs.tgbotapi)

    // Discord (Kord)
    implementation(libs.kord.core)

    // Logging
    implementation(libs.bundles.logging)

    // ACP (Agent Client Protocol)
    implementation(libs.acp)

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.testing)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.moatbot.MainKt")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.moatbot.MainKt"
    }
}

// Kover configuration for code coverage
kover {
    reports {
        total {
            xml {
                onCheck = true
            }
            html {
                onCheck = true
            }
        }
    }
}
