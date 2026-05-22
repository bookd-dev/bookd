val kotlin_version: String by project
val logback_version: String by project
val ktor_version: String by project
val exposed_version: String by project
val koin_version: String by project
val hikari_version: String by project
val postgres_version: String by project
val h2_version: String by project

plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    id("io.ktor.plugin") version "3.5.0"
    id("com.google.cloud.tools.jib") version "3.5.3"
}

group = "com.bookd"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

val webProjectDir = layout.projectDirectory.dir("../bookd-web")
val webDistDir = webProjectDir.dir("dist")
val generatedWebResourcesDir = layout.buildDirectory.dir("generated/resources/bookd-web")

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-auth")
    implementation("io.ktor:ktor-server-auth-jwt")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-server-config-yaml")
    implementation("io.ktor:ktor-server-forwarded-header")
    
    // Database
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposed_version")
    implementation("org.postgresql:postgresql:$postgres_version")
    implementation("com.h2database:h2:$h2_version")
    implementation("com.zaxxer:HikariCP:$hikari_version")
    
    // Dependency Injection
    implementation("io.insert-koin:koin-ktor:$koin_version")
    implementation("io.insert-koin:koin-logger-slf4j:$koin_version")
    
    // E-book parsing
    implementation("org.apache.tika:tika-core:3.3.0")
    implementation("org.apache.tika:tika-parsers-standard-package:3.3.0")
    implementation("org.jsoup:jsoup:1.22.2")
    
    // Redis
    implementation("io.lettuce:lettuce-core:7.5.2.RELEASE")
    implementation("org.apache.commons:commons-pool2:2.13.1")
    
    // Utilities
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.mindrot:jbcrypt:0.4")

    // Testing
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
    testImplementation("io.insert-koin:koin-test:$koin_version")
    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

val npmInstall by tasks.registering(Exec::class) {
    workingDir = webProjectDir.asFile
    commandLine("npm", "ci")
    inputs.file(webProjectDir.file("package.json"))
    inputs.file(webProjectDir.file("package-lock.json"))
    outputs.dir(webProjectDir.dir("node_modules"))
}

val buildBookdWeb by tasks.registering(Exec::class) {
    dependsOn(npmInstall)
    workingDir = webProjectDir.asFile
    commandLine("npm", "run", "build")
    inputs.file(webProjectDir.file("package.json"))
    inputs.file(webProjectDir.file("package-lock.json"))
    inputs.file(webProjectDir.file("index.html"))
    inputs.file(webProjectDir.file("vite.config.ts"))
    inputs.file(webProjectDir.file("tsconfig.json"))
    inputs.file(webProjectDir.file("tsconfig.app.json"))
    inputs.file(webProjectDir.file("tsconfig.node.json"))
    inputs.dir(webProjectDir.dir("src"))
    outputs.dir(webDistDir)
}

val copyBookdWebResources by tasks.registering(Copy::class) {
    dependsOn(buildBookdWeb)
    from(webDistDir)
    into(generatedWebResourcesDir.map { it.dir("static/web") })
}

sourceSets {
    main {
        resources.srcDir(generatedWebResourcesDir)
    }
}

tasks.named("processResources") {
    dependsOn(copyBookdWebResources)
}

jib {
    from {
        image = "eclipse-temurin:21-jre-alpine"
    }
    to {
        image = "bookd/server"
        tags = setOf("latest", version.toString())
    }
    container {
        ports = listOf("8080")
        environment = mapOf(
            "TZ" to "Asia/Shanghai"
        )
        jvmFlags = listOf(
            "-Xms512m",
            "-Xmx2048m",
            "-XX:+UseG1GC"
        )
    }
}
