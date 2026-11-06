import org.jetbrains.intellij.platform.gradle.TestFrameworkType

// IntelliJ 平台插件构建脚本：Java + Gradle Kotlin DSL

plugins {
    id("java")
    // IntelliJ Platform Gradle Plugin：打包、运行 IDE、发布插件
    id("org.jetbrains.intellij.platform") version "2.17.0"
}

group = "com.syrobin"
version = "2.2.0"

repositories {
    mavenCentral()
    // IntelliJ 制品与依赖的默认仓库（含 JetBrains 仓库）
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // 开发/运行插件所基于的 IDE 版本与平台测试框架
    intellijPlatform {
        val localIdePath = providers.gradleProperty("javaCodeGenerateIdePath").orNull
        if (localIdePath == null) {
            intellijIdea("2026.1")
        } else {
            local(localIdePath)
        }
        testFramework(TestFrameworkType.Platform)
        testBundledPlugin("com.intellij.java")
    }

    // 模板与工具库
    implementation("org.freemarker:freemarker:2.3.29")
    implementation("org.apache.commons:commons-lang3:3.9")
    implementation("com.google.guava:guava:27.0.1-jre")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.4")
    implementation("cn.hutool:hutool-all:5.6.3")
    implementation("redis.clients:jedis:3.1.0")
    implementation("mysql:mysql-connector-java:8.0.19")

    // 单元测试依赖（与 IntelliJ Platform TestFramework 原生兼容）
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    // 关闭可搜索选项构建以加快打包（发布时可按需开启）
    buildSearchableOptions = false

    pluginConfiguration {
        // 兼容的 IDE 构建号区间（sinceBuild / untilBuild）
        ideaVersion {
            sinceBuild = "233"
            untilBuild = "271.*"
        }
    }

    // 插件签名（发布到 JetBrains Marketplace 时使用，值来自环境变量）
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    // 发布令牌（上传插件时使用）
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

tasks {
    withType<JavaCompile> {
        // 使用 release 21 确保在 JDK 21 ~ JDK 25+ 编译时均生成兼容 IDEA JBR 21 的字节码
        sourceCompatibility = "21"
        targetCompatibility = "21"
        options.release.set(21)
        options.encoding = "UTF-8"
    }

    // 本地调试：启动带插件的沙箱 IDE
    runIde {
        jvmArgs("-Xmx2048m", "-Dfile.encoding=UTF-8")
    }
}
