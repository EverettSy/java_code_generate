//顶层结构
dependencies {
    implementation("org.freemarker:freemarker:2.3.29")
    implementation("org.apache.commons:commons-lang3:3.9")
    implementation("com.google.guava:guava:27.0.1-jre")
    implementation("cn.hutool:hutool-all:5.6.3")
    implementation("redis.clients:jedis:3.1.0")
    compileOnly("org.projectlombok:lombok:1.18.12")
    implementation("mysql:mysql-connector-java:8.0.19")
}


plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.4"
//    id("org.jetbrains.intellij.platform") version "2.0.0"
//    id("org.jetbrains.intellij.platform.migration") version "2.0.0"
    id("io.freefair.lombok") version "6.3.0"
}

group = "com"
version = "1.0.4"

repositories {
    mavenLocal()
    maven("https://maven.aliyun.com/nexus/content/repositories/central/")
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
//intellij {
//    //version.set("2021.3.3")
//    //type.set("IC") // Target IDE Platform
//
//    //
//}

intellij {
    version.set("2023.3.7")
    type.set("IC") // Target IDE Platform
    plugins.set(listOf(/* Plugin Dependencies */))
}


// 顶层结构
tasks.jar.configure {
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.INCLUDE
    from(configurations.runtimeClasspath.get().filter { it.name.endsWith("jar")}.map { zipTree(it) })
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    patchPluginXml {
        // 最低版本
        sinceBuild.set("233")
        // 最高版本
        untilBuild.set("242.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}


