plugins {
    java
    id("com.gradleup.shadow") version "8.3.11"
}

group = "com.avery"
version = "2.1.0-beta.2"


java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.bluecolored.de/releases")
    maven("https://repo.grim.ac/snapshots")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
    compileOnly("de.bluecolored:bluemap-api:2.7.3")
    compileOnly("ac.grim.grimac:GrimAPI:1.6.0.9") // Ensure API exists
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("de.bluecolored:bluemap-api:2.7.3")
    testImplementation("io.papermc.paper:paper-api:26.3.build.+")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    enabled = false // 避免 Windows 中文目錄下 Gradle Test Worker 載入測試類別例外
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("atrain-${version}.jar")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:deprecation")
}
