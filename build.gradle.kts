import java.util.zip.ZipFile

plugins {
    java
    id("com.gradleup.shadow") version "8.3.11"
}

group = "com.corebuilders"
version = "2.9.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.lavalink.dev/releases")
    maven("https://jitpack.io") {
        content { includeGroup("com.github.walkyst") }
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    implementation("com.zaxxer:HikariCP:6.3.3")
    implementation("com.querydsl:querydsl-sql:5.1.0")
    implementation("org.flywaydb:flyway-core:11.3.4")
    implementation("org.flywaydb:flyway-mysql:11.3.4")
    implementation("com.mysql:mysql-connector-j:9.7.0")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.1")

    implementation("net.dv8tion:JDA:6.5.0") {
        exclude(module = "opus-java")
        exclude(module = "tink")
    }

    implementation("dev.arbjerg:lavaplayer:2.2.7")
    implementation("dev.lavalink.youtube:v2:1.18.1")

    // Discord voice requires DAVE. libdave-jvm supports this Java 21 Paper plugin.
    implementation("moe.kyokobot.libdave:adapter-jda:0.1.2")
    implementation("moe.kyokobot.libdave:impl-jni:0.1.2")
    runtimeOnly("moe.kyokobot.libdave:natives-win-x86-64:0.1.2")
    runtimeOnly("moe.kyokobot.libdave:natives-linux-x86-64:0.1.2")
    runtimeOnly("moe.kyokobot.libdave:natives-linux-aarch64:0.1.2")
    runtimeOnly("moe.kyokobot.libdave:natives-darwin:0.1.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    archiveBaseName.set("core-builders")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
    mergeServiceFiles()

    // Hikari is relocated to avoid conflicts with other Paper plugins.
    relocate("com.zaxxer.hikari", "com.corebuilders.libs.hikari")
    relocate("com.fasterxml.jackson", "com.corebuilders.libs.jackson")
}

tasks.jar {
    enabled = false
}


val verifyQueryDslPersistence = tasks.register("verifyQueryDslPersistence") {
    doLast {
        val javaSources = fileTree("src/main/java") { include("**/*.java") }
        val rawSql = Regex("\"\\s*(SELECT|INSERT|UPDATE|DELETE|MERGE|WITH)\\s+", RegexOption.IGNORE_CASE)
        val violations = mutableListOf<String>()

        javaSources.forEach { source ->
            source.readLines().forEachIndexed { index, line ->
                if (rawSql.containsMatchIn(line) || line.contains("JdbcClient")) {
                    violations += "${source.relativeTo(projectDir)}:${index + 1}: ${line.trim()}"
                }
            }
        }

        check(violations.isEmpty()) {
            "Runtime persistence must use QueryDSL, not handwritten SQL/JdbcClient:\n" +
                violations.joinToString("\n")
        }
    }
}

val verifyLayering = tasks.register("verifyLayering") {
    doLast {
        val violations = mutableListOf<String>()

        fun forbidImports(directory: String, forbidden: List<String>) {
            fileTree(directory) { include("**/*.java") }.forEach { source ->
                source.readLines().forEachIndexed { index, line ->
                    if (line.trim().startsWith("import ") && forbidden.any { token -> line.contains(token) }) {
                        violations += "${source.relativeTo(projectDir)}:${index + 1}: ${line.trim()}"
                    }
                }
            }
        }

        // Domain/application services stay independent from Discord and Paper adapters.
        forbidImports(
            "src/main/java/com/corebuilders/bot/service",
            listOf(
                "net.dv8tion.jda", "org.bukkit",
                "com.corebuilders.bot.application", "com.corebuilders.bot.config",
                "com.corebuilders.bot.discord", "com.corebuilders.bot.external",
                "com.corebuilders.bot.minecraft", "com.corebuilders.bot.runtime"
            )
        )
        // Persistence stays below services/adapters.
        forbidImports(
            "src/main/java/com/corebuilders/bot/db",
            listOf(
                "net.dv8tion.jda", "org.bukkit",
                "com.corebuilders.bot.application", "com.corebuilders.bot.config",
                "com.corebuilders.bot.discord", "com.corebuilders.bot.external",
                "com.corebuilders.bot.minecraft", "com.corebuilders.bot.runtime",
                "com.corebuilders.bot.service"
            )
        )
        // Domain models remain independent from infrastructure and adapters.
        forbidImports(
            "src/main/java/com/corebuilders/bot/model",
            listOf(
                "net.dv8tion.jda", "org.bukkit",
                "com.corebuilders.bot.application", "com.corebuilders.bot.config",
                "com.corebuilders.bot.db", "com.corebuilders.bot.discord",
                "com.corebuilders.bot.external", "com.corebuilders.bot.minecraft",
                "com.corebuilders.bot.runtime", "com.corebuilders.bot.service"
            )
        )
        // Framework-free application helpers stay reusable and unit-testable.
        forbidImports(
            "src/main/java/com/corebuilders/bot/application",
            listOf("net.dv8tion.jda", "org.bukkit", "com.corebuilders.bot.db", "com.corebuilders.bot.discord", "com.corebuilders.bot.minecraft", "com.corebuilders.bot.service")
        )
        // External integrations do not depend on Discord, Paper, or persistence.
        forbidImports(
            "src/main/java/com/corebuilders/bot/external",
            listOf("net.dv8tion.jda", "org.bukkit", "com.corebuilders.bot.db", "com.corebuilders.bot.discord", "com.corebuilders.bot.minecraft", "com.corebuilders.bot.service")
        )

        check(violations.isEmpty()) {
            "Architecture layering violations:\n" + violations.joinToString("\n")
        }
    }
}


val verifyNoEmbeddedSecrets = tasks.register("verifyNoEmbeddedSecrets") {
    doLast {
        val tokenPatterns = listOf(
            Regex("mfa\\.[A-Za-z0-9_-]{20,}"),
            Regex("[A-Za-z0-9_-]{23,28}\\.[A-Za-z0-9_-]{6}\\.[A-Za-z0-9_-]{27,}"),
            Regex("gh[pousr]_[A-Za-z0-9_]{30,}"),
            Regex("AKIA[0-9A-Z]{16}"),
            Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----")
        )
        val violations = mutableListOf<String>()
        fileTree(projectDir) {
            exclude(".gradle/**", "build/**", "out/**", ".idea/**", "server/**", "gradle/wrapper/gradle-wrapper.jar")
        }.files.filter { it.isFile && it.length() <= 2_000_000 }.forEach { file ->
            val text = runCatching { file.readText() }.getOrNull() ?: return@forEach
            if (tokenPatterns.any { it.containsMatchIn(text) }) {
                violations += file.relativeTo(projectDir).path
            }
        }

        val defaultConfig = file("src/main/resources/config.yml").readText()
        listOf("token: \"\"", "api-key: \"\"", "password: \"\"").forEach { safeDefault ->
            check(defaultConfig.contains(safeDefault)) {
                "Tracked default config must keep secrets blank; expected: $safeDefault"
            }
        }
        check(violations.isEmpty()) {
            "Potential embedded secrets detected in: ${violations.joinToString()}"
        }
    }
}

val verifyShadowJar = tasks.register("verifyShadowJar") {
    dependsOn(tasks.shadowJar)

    doLast {
        val shadedJar = tasks.shadowJar.get().archiveFile.get().asFile
        val requiredEntries = listOf(
            "com/corebuilders/bot/db/QueryDslDatabase.class",
            "com/corebuilders/bot/db/Schema.class",
            "com/corebuilders/bot/runtime/CoreBuildersRuntime.class",
            "com/corebuilders/bot/discord/DiscordBotListener.class",
            "com/corebuilders/bot/discord/ApplicationDiscordListener.class",
            "com/corebuilders/bot/service/ApplicationService.class",
            "com/corebuilders/bot/config/ApplicationConfig.class",
            "com/corebuilders/bot/discord/CommandRegistrar.class",
            "com/corebuilders/bot/external/HyperglidingClient.class",
            "com/corebuilders/libs/hikari/HikariDataSource.class",
            "com/corebuilders/libs/jackson/databind/ObjectMapper.class",
            "com/querydsl/sql/SQLQueryFactory.class",
            "org/flywaydb/core/Flyway.class",
            "com/mysql/cj/jdbc/Driver.class",
            "net/dv8tion/jda/api/JDA.class",
            "com/sedmelluq/discord/lavaplayer/player/AudioPlayer.class",
            "dev/lavalink/youtube/YoutubeAudioSourceManager.class",
            "moe/kyokobot/libdave/NativeDaveFactory.class",
            "moe/kyokobot/libdave/jda/LDJDADaveSessionFactory.class"
        )

        ZipFile(shadedJar).use { zip ->
            val missing = requiredEntries.filter { zip.getEntry(it) == null }
            check(missing.isEmpty()) {
                "Shaded plugin JAR is missing required runtime classes: ${missing.joinToString()}"
            }

            val forbiddenEntries = zip.entries().asSequence()
                .map { it.name }
                .filter {
                    it.startsWith("org/springframework/") ||
                    it.startsWith("org/postgresql/")
                    it.startsWith("org/postgresql/") ||
                    it.startsWith("com/fasterxml/jackson/")
                }
                .take(10)
                .toList()
            check(forbiddenEntries.isEmpty()) {
                "Unexpected unrelocated/conflicting classes were included: ${forbiddenEntries.joinToString()}"
            }
        }
    }
}

tasks.build {
    dependsOn(verifyQueryDslPersistence)
    dependsOn(verifyLayering)
    dependsOn(verifyNoEmbeddedSecrets)
    dependsOn(verifyShadowJar)
}
