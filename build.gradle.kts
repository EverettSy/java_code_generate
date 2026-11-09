import org.jetbrains.intellij.platform.gradle.TestFrameworkType

// IntelliJ 平台插件构建脚本：Java + Gradle Kotlin DSL

plugins {
    id("java")
    // IntelliJ Platform Gradle Plugin：打包、运行 IDE、发布插件
    id("org.jetbrains.intellij.platform") version "2.17.0"
}

group = "com.syrobin"
version = "3.0.0"

repositories {
    mavenCentral()
    // IntelliJ 制品与依赖的默认仓库（含 JetBrains 仓库）
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // 开发/运行插件所基于的 IDE 版本与平台测试框架（IDEA 2026.3 / Java 25）
    intellijPlatform {
        val localIdePath = providers.gradleProperty("javaCodeGenerateIdePath").orNull
        if (localIdePath == null) {
            intellijIdea("2026.3")
        } else {
            local(localIdePath)
        }
        testFramework(TestFrameworkType.Platform)
        testBundledPlugin("com.intellij.java")
    }

    // 仅打包模板与 JSON 工具。JDBC 驱动由 DatabaseDialect 从本机仓库动态加载；不再打包 jedis/mysql/guava/hutool。
    implementation("org.freemarker:freemarker:2.3.35")
    implementation("org.apache.commons:commons-lang3:3.21.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.4")

    // 单元测试依赖（与 IntelliJ Platform TestFramework 原生兼容）
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    // 关闭可搜索选项构建以加快打包（发布时可按需开启）
    buildSearchableOptions = false

    pluginConfiguration {
        // 兼容的 IDE 构建号区间（sinceBuild / untilBuild）
        ideaVersion {
            // 223 = 2022.3（JBR 17 起点），配合 --release 17 字节码覆盖 2022.3 ~ 2027.1 全量主流存量用户
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
    register("checkReleaseTag") {
        val expectedTag = "v${project.version}"
        val releaseTag = providers.environmentVariable("GITHUB_REF_NAME").orNull
        doLast {
            check(releaseTag == expectedTag) {
                "Release tag '${releaseTag ?: "(unset)"}' does not match project version. Expected '$expectedTag'."
            }
        }
    }

    withType<JavaCompile> {
        // 使用 JDK 25 编译并输出 Java 17 字节码（class version 61），兼容 IDEA 2023.3 (JBR 17) ~ 2026.3+ (JBR 25)
        sourceCompatibility = "17"
        targetCompatibility = "17"
        options.release.set(17)
        options.encoding = "UTF-8"
    }

    // 本地调试：启动带插件的沙箱 IDE
    runIde {
        jvmArgs("-Xmx2048m", "-Dfile.encoding=UTF-8")
    }

    named("publishPlugin") {
        dependsOn("checkReleaseTag")
    }
}
