// IntelliJ 平台插件构建脚本：Java + Gradle Kotlin DSL

plugins {
    id("java")
    // IntelliJ Platform Gradle Plugin：打包、运行 IDE、发布插件
    id("org.jetbrains.intellij.platform") version "2.13.1"
}

group = "com"
version = "2.0.0"

repositories {
    mavenCentral()
    // IntelliJ 制品与依赖的默认仓库（含 JetBrains 仓库）
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // 开发/运行插件所基于的 IDE 版本
    intellijPlatform {
        intellijIdea("2026.1")
    }

    // 模板与工具库
    implementation("org.freemarker:freemarker:2.3.29")
    implementation("org.apache.commons:commons-lang3:3.9")
    implementation("com.google.guava:guava:27.0.1-jre")
    implementation("cn.hutool:hutool-all:5.6.3")
    implementation("redis.clients:jedis:3.1.0")
    implementation("mysql:mysql-connector-java:8.0.19")

    // 仅编译期：Lombok 由注解处理器生成样板代码
    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")
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
        sourceCompatibility = "21"
        targetCompatibility = "21"
        options.encoding = "UTF-8"
    }

    // 本地调试：启动带插件的沙箱 IDE
    runIde {
        jvmArgs("-Xmx2048m", "-Dfile.encoding=UTF-8")
    }
}
