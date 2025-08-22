import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.20"
    kotlin("plugin.serialization") version "1.9.20"
    application
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin standard library
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")
    
    // Kotlin scripting for dynamic compilation
    implementation("org.jetbrains.kotlin:kotlin-scripting-jsr223:1.9.20")
    implementation("org.jetbrains.kotlin:kotlin-scripting-common:1.9.20")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:1.9.20")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host:1.9.20")
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.9.20")
    
    // Ktor server
    implementation("io.ktor:ktor-server-core:2.3.5")
    implementation("io.ktor:ktor-server-netty:2.3.5")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.5")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.5")
    implementation("io.ktor:ktor-server-cors:2.3.5")
    implementation("io.ktor:ktor-server-call-logging:2.3.5")
    
    // JSON processing
    implementation("org.json:json:20230618")
    
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.11")
    
    // Testing
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

application {
    mainClass.set("com.example.compilation.ApplicationKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}