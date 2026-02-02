import nl.littlerobots.vcu.plugin.resolver.VersionSelectors

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    alias(libs.plugins.kover)
    alias(libs.plugins.version.catalog.update)
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

versionCatalogUpdate { versionSelector(VersionSelectors.STABLE) }

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
        // Exclude external library wrappers and entry points from coverage
        filters {
            excludes {
                classes(
                    // External library wrappers (thin adapters with direct 3rd party API calls)
                    "com.moatbot.infrastructure.messaging.discord.DiscordClient",
                    "com.moatbot.infrastructure.messaging.telegram.TelegramClient",
                    "com.moatbot.infrastructure.ClaudeCli",
                    // Entry point and configuration
                    "com.moatbot.MainKt",
                    "com.moatbot.Config",
                )
            }
        }
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
