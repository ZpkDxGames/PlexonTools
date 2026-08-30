import java.io.OutputStream
import java.util.zip.ZipFile

plugins {
    java
}

group = "com.plexon"
version = "3.6.1"

val pluginVersion = version.toString()

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:deprecation", "-Xlint:-processing"))
}

tasks.processResources {
    val resourceProperties = mapOf("version" to pluginVersion)
    inputs.properties(resourceProperties)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(resourceProperties)
    }
}

tasks.test {
    useJUnitPlatform()
    systemProperty("plexontools.test-classpath", sourceSets.test.get().runtimeClasspath.asPath)
}

tasks.jar {
    archiveBaseName.set("PlexonTools")
    archiveVersion.set(pluginVersion)
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { dependency ->
        if (dependency.isDirectory) dependency else zipTree(dependency)
    })
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
    manifest {
        attributes(
            "Implementation-Title" to "PlexonTools",
            "Implementation-Version" to pluginVersion,
            "Implementation-Vendor" to "ZpkDxGames",
            "Multi-Release" to "true"
        )
    }
}

val verifyPluginJar by tasks.registering {
    group = "verification"
    description = "Opens and fully reads the release JAR, including bundled SQLite natives."
    dependsOn(tasks.jar)
    inputs.file(tasks.jar.flatMap { it.archiveFile })

    doLast {
        val jarFile = tasks.jar.get().archiveFile.get().asFile
        val requiredEntries = listOf(
            "plugin.yml",
            "org/sqlite/JDBC.class",
            "org/sqlite/native/Linux/x86_64/libsqlitejdbc.so",
            "org/sqlite/native/Mac/x86_64/libsqlitejdbc.dylib",
            "org/sqlite/native/Windows/x86_64/sqlitejdbc.dll"
        )
        ZipFile(jarFile).use { archive ->
            requiredEntries.forEach { name ->
                check(archive.getEntry(name) != null) {
                    "Release JAR is missing required entry: $name"
                }
            }
            val entries = archive.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.isDirectory) {
                    archive.getInputStream(entry).use { input ->
                        input.transferTo(OutputStream.nullOutputStream())
                    }
                }
            }
        }
    }
}

tasks.check {
    dependsOn(verifyPluginJar)
}
