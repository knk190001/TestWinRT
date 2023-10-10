import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.21"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("MainKt")
    applicationDefaultJvmArgs += "--enable-native-access=ALL-UNNAMED"
}

repositories {
    mavenCentral()

    maven {
        url = uri("https://sfxdev-101802139621.d.codeartifact.us-west-2.amazonaws.com/maven/KotlinWinRT/")
        credentials {
            username = "aws"
            password = System.getenv("CODEARTIFACT_AUTH_TOKEN")
        }
    }
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.github.knk190001:windows-kt:0.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.21")
    implementation(kotlin("stdlib"))

}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        vendor.set(JvmVendorSpec.ADOPTIUM)
        languageVersion.set(JavaLanguageVersion.of(19))
    }
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "19"
}

kotlin {
    sourceSets.all {
        languageSettings {
            languageVersion = "2.0"
        }
    }
}

tasks.create("printRuntimeClasspath") {
    val runtimeClasspath = sourceSets.main.get().runtimeClasspath
    inputs.files(runtimeClasspath)
    doLast {
        println(sourceSets["main"].runtimeClasspath.joinToString("\n"))
    }
}