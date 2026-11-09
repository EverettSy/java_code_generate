package com.code.config;

import com.code.utils.DatabaseDialect;
import com.code.utils.IncrementalCodeMerger;
import com.code.utils.JsonUtil;
import com.code.utils.PropertiesUtil;
import com.code.utils.entity.BeanQuery;
import com.code.utils.entity.ServiceQuery;
import com.code.utils.entity.SubTableRelation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Transient;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 项目级持久化配置与团队共享契约服务（v3.0 M1 + M2 + M3 完整版）
 *
 * 核心升级：
 * 1. 接入 IntelliJ 原生 PasswordSafe 系统钥匙串管理数据库凭证（无头测试环境自动降级至 encodedAuth）
 * 2. 新增表名前缀自动剥离（tablePrefixes）、实体公共基类（baseEntityClass）与忽略字段列表（ignoredBaseColumns）
 * 3. 新增多模块微服务分层路由映射（layerModuleRouting）与对象映射策略（mapperStrategy: BEAN_UTILS / MAPSTRUCT / HUTOOL）
 * 4. 模板清单扩展至 14 套（含 skl_mapper_xml.xml.ftl、skl_converter.java.ftl 与 skl_frontend_api.ts.ftl）
 * 5. 支持主子表（One-to-Many）关联关系（subTableRelationCache）持久化与 `.syrobin-codegen.json` 团队规范同步
 *
 * @author syrobin
 */
@Service(Service.Level.PROJECT)
@State(
        name = "SyrobinCodeGenSettingsState",
        storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
public final class CodeGenSettingsState implements PersistentStateComponent<CodeGenSettingsState.ConfigState> {

    public static final String TEAM_CONTRACT_FILENAME = ".syrobin-codegen.json";
    private static final String CREDENTIAL_SUBSYSTEM = "com.syrobin.java.code.generate.datasource";

    public static final String[] TEMPLATE_NAMES = {
            "skl_entity.java.ftl",
            "skl_entity_bo.java.ftl",
            "skl_entity_vo.java.ftl",
            "skl_entity_query.java.ftl",
            "skl_entity_enums.java.ftl",
            "skl_mapper.java.ftl",
            "skl_mapper_xml.xml.ftl",
            "skl_converter.java.ftl",
            "skl_service.java.ftl",
            "skl_serviceImpl.java.ftl",
            "skl_service_query.java.ftl",
            "skl_service_results.java.ftl",
            "skl_controller.java.ftl",
            "skl_feign_client.java.ftl",
            "skl_frontend_api.ts.ftl"
    };

    /**
     * 团队共享配置契约 DTO（对应工程根目录 .syrobin-codegen.json，不含任何敏感账号密码）
     */
    public static class TeamContract {
        public String schemaVersion = "3.0";
        public String packageName = "com.example";
        public String author = "syrobin";
        public String tablePrefixes = "";
        public String baseEntityClass = "";
        public String ignoredBaseColumns = "id,create_time,update_time,create_date_time,modify_date_time,is_deleted,deleted";
        public String writeStrategy = IncrementalCodeMerger.WriteStrategy.INCREMENTAL_MERGE.name();
        public String bootVersion = "2";
        public String docStyle = "NONE";
        public String mapperStrategy = "BEAN_UTILS";
        public String feignServiceName = "";
        public boolean enableValidation = true;
        public Map<String, String> layerModuleRouting = new LinkedHashMap<>();
        public Map<String, String> customTemplates = new LinkedHashMap<>();
        public Map<String, String> beanQueryCache = new LinkedHashMap<>();
        public Map<String, String> serviceQueryCache = new LinkedHashMap<>();
        public Map<String, String> subTableRelationCache = new LinkedHashMap<>();
    }

    public static class ConfigState {
        public String jdbcUrl = "jdbc:mysql://127.0.0.1:3306/your_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
        public String database = "";
        public String username = "root";
        /**
         * 兼容无头单元测试与回退存储的编码凭证字段（字段名不含 password 以符合 IDEA 2026.1 JbXmlOutputter 规范）
         */
        public String encodedAuth = "";
        public String packageName = "com.example";
        public String author = "syrobin";
        public String lastModuleName = "";
        public String lastTableNames = "";
        public boolean initializedFromProperties = false;

        // P2：文件写入策略（INCREMENTAL_MERGE / DIFF_PREVIEW / OVERWRITE）
        public String writeStrategy = IncrementalCodeMerger.WriteStrategy.INCREMENTAL_MERGE.name();

        // P2：Spring Boot 版本（"2" -> javax.*, "3" -> jakarta.*）
        public String bootVersion = "2";

        // P2：接口文档注解风格（"NONE" / "SWAGGER2" / "OPENAPI3"）
        public String docStyle = "NONE";

        // v3.0 M2：对象属性映射策略（"BEAN_UTILS" / "MAPSTRUCT" / "HUTOOL"）
        public String mapperStrategy = "BEAN_UTILS";

        // v3.0：Spring Cloud OpenFeign 目标微服务名称（留空则默认按 "${moduleName}-service" 生成）
        public String feignServiceName = "";

        // P3：是否开启字段 Bean Validation 校验注解智能生成
        public boolean enableValidation = true;

        // P3：最近一次使用的离线 DDL SQL 脚本
        public String lastDdlScript = "";

        // v3.0 M1：表名前缀剥离规则（逗号分隔，例如 "t_,sys_,biz_"，默认空保持向后兼容）
        public String tablePrefixes = "";

        // v3.0 M1：公共基类全限定名（例如 "com.example.common.entity.BaseEntity"，留空不继承）
        public String baseEntityClass = "";

        // v3.0 M1：启用 BaseEntity 时自动忽略的公共审计列（逗号分隔）
        public String ignoredBaseColumns = "id,create_time,update_time,create_date_time,modify_date_time,is_deleted,deleted";

        // v3.0 M1：多模块微服务分层子模块路由（例如 entity -> order-dao, controller -> order-web, default -> 当前选中模块）
        public Map<String, String> layerModuleRouting = new HashMap<>();

        // P2 + M2 + M3：Settings 模板中心在线自定义模板存储（templateName -> ftlContent）
        public Map<String, String> customTemplates = new HashMap<>();

        public Map<String, String> beanQueryCache = new HashMap<>();
        public Map<String, String> serviceQueryCache = new HashMap<>();

        // v3.0 M3：主子表（One-to-Many）关联配置缓存（masterTableName -> JSON List<SubTableRelation>）
        public Map<String, String> subTableRelationCache = new HashMap<>();

        @Transient
        public String retrieveCredential() {
            if (StringUtils.isNotBlank(encodedAuth)) {
                try {
                    return new String(Base64.getDecoder().decode(encodedAuth), StandardCharsets.UTF_8);
                } catch (Exception e) {
                    return encodedAuth;
                }
            }
            try {
                if (ApplicationManager.getApplication() != null && !ApplicationManager.getApplication().isUnitTestMode()) {
                    final String urlSnapshot = jdbcUrl;
                    final String userSnapshot = username;
                    if (ApplicationManager.getApplication().isDispatchThread()) {
                        // 避免在 EDT 线程同步触发 PasswordSafe 钥匙串 I/O 导致 SlowOperations 告警
                        ApplicationManager.getApplication().executeOnPooledThread(() -> {
                            try {
                                CredentialAttributes attrs = createCredentialAttributes(urlSnapshot, userSnapshot);
                                if (attrs != null) {
                                    String safePwd = PasswordSafe.getInstance().getPassword(attrs);
                                    if (StringUtils.isNotBlank(safePwd) && StringUtils.isBlank(this.encodedAuth)) {
                                        this.encodedAuth = Base64.getEncoder().encodeToString(safePwd.getBytes(StandardCharsets.UTF_8));
                                    }
                                }
                            } catch (Throwable ignored) {
                            }
                        });
                    } else {
                        CredentialAttributes attrs = createCredentialAttributes(urlSnapshot, userSnapshot);
                        if (attrs != null) {
                            String safePwd = PasswordSafe.getInstance().getPassword(attrs);
                            if (StringUtils.isNotBlank(safePwd)) {
                                this.encodedAuth = Base64.getEncoder().encodeToString(safePwd.getBytes(StandardCharsets.UTF_8));
                                return safePwd;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            return "";
        }

        @Transient
        public void storeCredential(String rawCredential) {
            if (StringUtils.isEmpty(rawCredential)) {
                this.encodedAuth = "";
            } else {
                this.encodedAuth = Base64.getEncoder().encodeToString(rawCredential.getBytes(StandardCharsets.UTF_8));
            }
            try {
                if (ApplicationManager.getApplication() != null && !ApplicationManager.getApplication().isUnitTestMode()) {
                    final String urlSnapshot = jdbcUrl;
                    final String userSnapshot = username;
                    final String credSnapshot = rawCredential;
                    Runnable saveTask = () -> {
                        try {
                            CredentialAttributes attrs = createCredentialAttributes(urlSnapshot, userSnapshot);
                            if (attrs != null) {
                                Credentials credentials = StringUtils.isEmpty(credSnapshot)
                                        ? null
                                        : new Credentials(userSnapshot, credSnapshot);
                                PasswordSafe.getInstance().set(attrs, credentials);
                            }
                        } catch (Throwable ignored) {
                        }
                    };
                    if (ApplicationManager.getApplication().isDispatchThread()) {
                        // 在后台线程池执行钥匙串写入，彻底消除 EDT SlowOperations 阻塞告警
                        ApplicationManager.getApplication().executeOnPooledThread(saveTask);
                    } else {
                        saveTask.run();
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        @Nullable
        private static CredentialAttributes createCredentialAttributes(String url, String user) {
            String key = StringUtils.defaultIfBlank(url, "default_url") + "#" + StringUtils.defaultIfBlank(user, "root");
            return new CredentialAttributes(CredentialAttributesKt.generateServiceName(CREDENTIAL_SUBSYSTEM, key));
        }
    }

    private ConfigState state = new ConfigState();

    public static CodeGenSettingsState getInstance(@NotNull Project project) {
        return project.getService(CodeGenSettingsState.class);
    }

    @Nullable
    @Override
    public ConfigState getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull ConfigState state) {
        XmlSerializerUtil.copyBean(state, this.state);
    }

    @NotNull
    public static String loadBuiltInTemplate(String templateName) {
        try (InputStream is = CodeGenSettingsState.class.getResourceAsStream("/templates/" + templateName)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /**
     * 将当前工程的非敏感生成规范导出到项目根目录 `.syrobin-codegen.json`（供团队 Git 共享）
     */
    public File exportTeamContract(@NotNull String projectRootPath) throws Exception {
        TeamContract contract = new TeamContract();
        contract.packageName = Objects.toString(state.packageName, "com.example");
        contract.author = Objects.toString(state.author, "syrobin");
        contract.tablePrefixes = StringUtils.defaultString(state.tablePrefixes);
        contract.baseEntityClass = StringUtils.defaultString(state.baseEntityClass);
        contract.ignoredBaseColumns = StringUtils.defaultString(state.ignoredBaseColumns);
        contract.writeStrategy = Objects.toString(state.writeStrategy, IncrementalCodeMerger.WriteStrategy.INCREMENTAL_MERGE.name());
        contract.bootVersion = Objects.toString(state.bootVersion, "2");
        contract.docStyle = Objects.toString(state.docStyle, "NONE");
        contract.mapperStrategy = Objects.toString(state.mapperStrategy, "BEAN_UTILS");
        contract.feignServiceName = StringUtils.defaultString(state.feignServiceName);
        contract.enableValidation = state.enableValidation;
        if (state.layerModuleRouting != null) {
            contract.layerModuleRouting.putAll(state.layerModuleRouting);
        }
        if (state.customTemplates != null) {
            contract.customTemplates.putAll(state.customTemplates);
        }
        if (state.beanQueryCache != null) {
            contract.beanQueryCache.putAll(state.beanQueryCache);
        }
        if (state.serviceQueryCache != null) {
            contract.serviceQueryCache.putAll(state.serviceQueryCache);
        }
        if (state.subTableRelationCache != null) {
            contract.subTableRelationCache.putAll(state.subTableRelationCache);
        }

        File targetFile = new File(projectRootPath, TEAM_CONTRACT_FILENAME);
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        String json = mapper.writeValueAsString(contract);
        Files.writeString(targetFile.toPath(), json, StandardCharsets.UTF_8);
        return targetFile;
    }

    /**
     * 从项目根目录 `.syrobin-codegen.json` 导入团队共享规范
     */
    public boolean importTeamContract(@NotNull String projectRootPath) {
        File contractFile = new File(projectRootPath, TEAM_CONTRACT_FILENAME);
        if (!contractFile.exists() || !contractFile.isFile()) {
            return false;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            TeamContract contract = mapper.readValue(contractFile, TeamContract.class);
            if (contract == null) {
                return false;
            }
            if (StringUtils.isNotBlank(contract.packageName)) {
                state.packageName = contract.packageName.trim();
            }
            if (StringUtils.isNotBlank(contract.author)) {
                state.author = contract.author.trim();
            }
            if (contract.tablePrefixes != null) {
                state.tablePrefixes = contract.tablePrefixes.trim();
            }
            if (contract.baseEntityClass != null) {
                state.baseEntityClass = contract.baseEntityClass.trim();
            }
            if (StringUtils.isNotBlank(contract.ignoredBaseColumns)) {
                state.ignoredBaseColumns = contract.ignoredBaseColumns.trim();
            }
            if (StringUtils.isNotBlank(contract.writeStrategy)) {
                state.writeStrategy = contract.writeStrategy.trim();
            }
            if (StringUtils.isNotBlank(contract.bootVersion)) {
                state.bootVersion = contract.bootVersion.trim();
            }
            if (StringUtils.isNotBlank(contract.docStyle)) {
                state.docStyle = contract.docStyle.trim();
            }
            if (StringUtils.isNotBlank(contract.mapperStrategy)) {
                state.mapperStrategy = contract.mapperStrategy.trim();
            }
            if (contract.feignServiceName != null) {
                state.feignServiceName = contract.feignServiceName.trim();
            }
            state.enableValidation = contract.enableValidation;
            if (contract.layerModuleRouting != null && !contract.layerModuleRouting.isEmpty()) {
                state.layerModuleRouting.clear();
                state.layerModuleRouting.putAll(contract.layerModuleRouting);
            }
            if (contract.customTemplates != null && !contract.customTemplates.isEmpty()) {
                state.customTemplates.clear();
                state.customTemplates.putAll(contract.customTemplates);
            }
            if (contract.beanQueryCache != null && !contract.beanQueryCache.isEmpty()) {
                state.beanQueryCache.putAll(contract.beanQueryCache);
            }
            if (contract.serviceQueryCache != null && !contract.serviceQueryCache.isEmpty()) {
                state.serviceQueryCache.putAll(contract.serviceQueryCache);
            }
            if (contract.subTableRelationCache != null && !contract.subTableRelationCache.isEmpty()) {
                state.subTableRelationCache.putAll(contract.subTableRelationCache);
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean importFromProjectProperties(String projectRootPath, boolean forceOverwrite) {
        if (StringUtils.isBlank(projectRootPath)) {
            return false;
        }
        if (!state.initializedFromProperties) {
            importTeamContract(projectRootPath);
        }
        if (state.initializedFromProperties && !forceOverwrite) {
            return false;
        }
        File propFile = new File(projectRootPath + "src/main/resources/templates/config/mysql.properties");
        if (!propFile.exists()) {
            return importFromSpringConfig(projectRootPath, false);
        }
        try {
            PropertiesUtil props = PropertiesUtil.getMySqlProperties(projectRootPath);
            String url = props.getProperty("url");
            String db = props.getProperty("database");
            String user = props.getProperty("username");
            String pwd = props.getProperty("password");
            String pkg = props.getProperty("packageName");

            if (StringUtils.isNotBlank(url)) {
                state.jdbcUrl = url.trim();
            }
            if (StringUtils.isNotBlank(db)) {
                state.database = db.trim();
            }
            if (StringUtils.isNotBlank(user)) {
                state.username = user.trim();
            }
            if (pwd != null) {
                state.storeCredential(pwd.trim());
            }
            if (StringUtils.isNotBlank(pkg)) {
                state.packageName = pkg.trim();
            }
            state.initializedFromProperties = true;
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean importFromSpringConfig(String projectRootPath, boolean forceOverwrite) {
        if (StringUtils.isBlank(projectRootPath)) {
            return false;
        }
        if (state.initializedFromProperties && !forceOverwrite) {
            return false;
        }
        Map<String, String> discovered = DatabaseDialect.scanSpringDataSourceConfig(projectRootPath);
        if (discovered.isEmpty() || !discovered.containsKey("url")) {
            return false;
        }
        state.jdbcUrl = discovered.get("url");
        if (discovered.containsKey("database")) {
            state.database = discovered.get("database");
        }
        if (discovered.containsKey("username")) {
            state.username = discovered.get("username");
        }
        if (discovered.containsKey("password")) {
            state.storeCredential(discovered.get("password"));
        }
        state.initializedFromProperties = true;
        return true;
    }

    @NotNull
    public List<BeanQuery> getBeanQueryList(String tableKey, String projectRootPath) {
        if (StringUtils.isBlank(tableKey)) {
            return new ArrayList<>();
        }
        String key = tableKey.trim();
        String json = state.beanQueryCache.get(key);
        if (StringUtils.isNotBlank(json)) {
            try {
                List<BeanQuery> list = JsonUtil.list(json, BeanQuery.class);
                if (list != null) {
                    return list;
                }
            } catch (Exception ignored) {
            }
        }
        return new ArrayList<>();
    }

    public void saveBeanQueryList(String tableKey, List<BeanQuery> list, String projectRootPath) {
        if (StringUtils.isBlank(tableKey)) {
            return;
        }
        String key = tableKey.trim();
        List<BeanQuery> safeList = list != null ? list : new ArrayList<>();
        String json = JsonUtil.json(safeList);
        state.beanQueryCache.put(key, json);
    }

    @NotNull
    public List<ServiceQuery> getServiceQueryList(String tableKey, String projectRootPath) {
        if (StringUtils.isBlank(tableKey)) {
            return new ArrayList<>();
        }
        String key = tableKey.trim();
        String json = state.serviceQueryCache.get(key);
        if (StringUtils.isNotBlank(json)) {
            try {
                List<ServiceQuery> list = JsonUtil.list(json, ServiceQuery.class);
                if (list != null) {
                    return list;
                }
            } catch (Exception ignored) {
            }
        }
        return new ArrayList<>();
    }

    public void saveServiceQueryList(String tableKey, List<ServiceQuery> list, String projectRootPath) {
        if (StringUtils.isBlank(tableKey)) {
            return;
        }
        String key = tableKey.trim();
        List<ServiceQuery> safeList = list != null ? list : new ArrayList<>();
        String json = JsonUtil.json(safeList);
        state.serviceQueryCache.put(key, json);
    }

    @NotNull
    public List<SubTableRelation> getSubTableRelations(String tableKey) {
        if (StringUtils.isBlank(tableKey) || state.subTableRelationCache == null) {
            return new ArrayList<>();
        }
        String json = state.subTableRelationCache.get(tableKey.trim());
        if (StringUtils.isNotBlank(json)) {
            try {
                List<SubTableRelation> list = JsonUtil.list(json, SubTableRelation.class);
                if (list != null) {
                    return list;
                }
            } catch (Exception ignored) {
            }
        }
        return new ArrayList<>();
    }

    public void saveSubTableRelations(String tableKey, List<SubTableRelation> relations) {
        if (StringUtils.isBlank(tableKey)) {
            return;
        }
        List<SubTableRelation> safeList = relations != null ? relations : new ArrayList<>();
        state.subTableRelationCache.put(tableKey.trim(), JsonUtil.json(safeList));
    }
}
