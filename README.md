# Syrobin Java Code Generate

面向 IntelliJ IDEA 的 Java 后端 / 全栈代码生成插件。根据数据库表或离线 DDL，一键生成 Entity、BO、VO、Mapper、Service、Controller、Feign、前端 TypeScript API 等分层代码，并支持 PSI 增量合并与批量 Diff 预演。

| 项 | 值 |
|---|---|
| 插件 ID | `com.syrobin.java.code.generate` |
| 当前版本 | `4.0.0` |
| 兼容 IDE | IntelliJ IDEA **2023.3+**（`sinceBuild=233`）~ **2027.1.***（`untilBuild=271.*`） |
| 字节码 | Java 17（`--release 17`），可用 JDK 25 编译 |
| 作者 | [syrobin](https://github.com/EverettSy) |

## 核心能力

- **20 套 FreeMarker 模板**：Entity / BO / VO / Query / Enum / Mapper / Mapper.xml / MapStruct Converter / Service / ServiceImpl / ServiceQuery / ServiceResults / Controller / Feign Client / Frontend TypeScript(Axios) API / Vue 3 CRUD 页面 / HTTP Client 脚本 / EasyExcel DTO / JUnit 5 测试 / Flyway 迁移；另支持 Controller/接口转 Feign。
- **智能推断与扩展**：规则版自然语言建表、主子表关系识别、查询规则推断；`postProcessor` 扩展点与自定义模板包。
- **多方言元数据**：MySQL、PostgreSQL、Oracle、达梦（DM8）；支持 IDEA Database 面板右键生成，以及 100% 离线 DDL 解析（含 `COMMENT ON`、`ALTER TABLE ADD COLUMN`）。
- **PSI 增量合并**：基于 `PsiJavaFile` AST 合并字段 / 枚举项 / import，配合 `CodeStyleManager` 格式化；单文件或批量 `DiffRequestChain` 预演；`LocalHistory` 一键回滚最近生成批次。
- **多模块投递与主子表**：按层路由到 Maven/Gradle 子模块；表前缀剥离、`BaseEntity` 继承；One-to-Many 主子表级联生成。
- **团队契约与工作台**：`.syrobin-codegen.json` 可纳入 Git；`PasswordSafe` 存库密码；右侧常驻 `Syrobin CodeGen` ToolWindow；Settings 内模板实时预览。

## 安装

1. **Marketplace**：在 IDEA 插件市场搜索 `Syrobin Java Code Generate` 安装。
2. **本地 zip**：`./gradlew buildPlugin` 后安装 `build/distributions/java_code_generate-4.0.0.zip`。

需要 Database 面板右键生成时，请启用宿主自带的 **Database Tools** 插件（本插件对其为可选依赖）。

## 快速使用

1. 在 Project 视图右键 → **Syrobin Code Generate**，或打开右侧 **Syrobin CodeGen** 工具窗口。
2. 选择数据源方式：
   - 连接数据库拉取表结构
   - Database 工具窗口选中表后生成
   - 选中 DDL SQL 文件 / 脚本离线解析
3. 勾选要生成的层与目标模块路径，确认后预览 Diff，再写入磁盘。
4. 全局策略与模板：`Settings → Tools → Syrobin Code Generate`。

常用菜单项：

| 菜单 | 说明 |
|---|---|
| Generate All Code | 按配置一键生成各层 |
| Generate Code from Selected Database Tables | 从 Database 选中表生成 |
| Generate Code from DDL SQL (Offline) | 离线 DDL 生成 |
| Generate Feign Client | 生成 OpenFeign 接口 |
| Convert Interface / Controller to Feign Client | 将现有 Controller / 接口转为 `@FeignClient` |

## 本地开发

环境要求：

- JDK **17+**（推荐 Temurin 25，与 CI 一致）
- Git + Gradle Wrapper（仓库已含 `gradlew`）

常用命令：

```bash
# 单元测试
./gradlew test

# 打包插件 zip
./gradlew buildPlugin

# 启动带本插件的沙箱 IDE
./gradlew runIde

# 可选：Plugin Verifier（耗时，会下载多版本 IDE）
./gradlew verifyPlugin
```

可选：通过 Gradle 属性指定本机 IDE，避免重复下载平台：

```properties
# gradle.properties 或 -P
javaCodeGenerateIdePath=/path/to/IntelliJ IDEA.app/Contents
```

未设置时默认使用 `intellijIdea("2026.3")`。

## 发布

打标签 `v{version}`（须与 `build.gradle.kts` 中 `version` 一致，例如 `v4.0.0`）触发 GitHub Actions：`test` → `buildPlugin` → `verifyPlugin` → `publishPlugin`。

需要配置 Secrets：`CERTIFICATE_CHAIN`、`PRIVATE_KEY`、`PRIVATE_KEY_PASSWORD`、`PUBLISH_TOKEN`。

## 目录结构（简要）

```
src/main/java/com/code/     # Action / UI / 配置 / 生成与合并工具
src/main/resources/
  META-INF/plugin.xml       # 插件声明与菜单
  templates/*.ftl           # FreeMarker 模板
  messages/                 # 中英 i18n
```

## License

以仓库实际声明为准；插件厂商信息见 `plugin.xml` 中的 `vendor`。
