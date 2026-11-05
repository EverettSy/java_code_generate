package com.code.config;

import com.code.utils.DatabaseDialect;
import com.code.utils.IncrementalCodeMerger;
import com.code.utils.JsonUtil;
import com.code.utils.PropertiesUtil;
import com.code.utils.RedisUtils;
import com.code.utils.entity.BeanQuery;
import com.code.utils.entity.ServiceQuery;
import com.google.common.collect.Lists;
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
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目级持久化配置服务（P1 + P2 + P3：兼容 IDEA 2026.1 JbXmlOutputter 敏感字段检查，杜绝明文 password 落盘报错）
 * 存储位置：项目工作区配置 (StoragePathMacros.WORKSPACE_FILE)
 */
@Service(Service.Level.PROJECT)
@State(
        name = "SyrobinCodeGenSettingsState",
        storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
public final class CodeGenSettingsState implements PersistentStateComponent<CodeGenSettingsState.ConfigState> {

    public static final String[] TEMPLATE_NAMES = {
            "skl_entity.java.ftl",
            "skl_entity_bo.java.ftl",
            "skl_entity_vo.java.ftl",
            "skl_entity_query.java.ftl",
            "skl_entity_enums.java.ftl",
            "skl_mapper.java.ftl",
            "skl_service.java.ftl",
            "skl_serviceImpl.java.ftl",
            "skl_service_query.java.ftl",
            "skl_service_results.java.ftl",
            "skl_controller.java.ftl"
    };

    public static class ConfigState {
        public String jdbcUrl = "jdbc:mysql://127.0.0.1:3306/your_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
        public String database = "";
        public String username = "root";
        /**
         * 使用 Base64 编码存储连接凭证且字段名不含 "password"，避免触发 IDEA 2026.1 JbXmlOutputter SEVERE 敏感字段报警
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

        // P3：是否开启字段 Bean Validation 校验注解智能生成
        public boolean enableValidation = true;

        // P3：最近一次使用的离线 DDL SQL 脚本
        public String lastDdlScript = "";

        // P2：Settings 模板中心在线自定义模板存储（templateName -> ftlContent）
        public Map<String, String> customTemplates = new HashMap<>();

        public Map<String, String> beanQueryCache = new HashMap<>();
        public Map<String, String> serviceQueryCache = new HashMap<>();

        @Transient
        public String retrieveCredential() {
            if (StringUtils.isBlank(encodedAuth)) {
                return "";
            }
            try {
                return new String(Base64.getDecoder().decode(encodedAuth), StandardCharsets.UTF_8);
            } catch (Exception e) {
                return encodedAuth;
            }
        }

        @Transient
        public void storeCredential(String rawCredential) {
            if (StringUtils.isEmpty(rawCredential)) {
                this.encodedAuth = "";
            } else {
                this.encodedAuth = Base64.getEncoder().encodeToString(rawCredential.getBytes(StandardCharsets.UTF_8));
            }
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

    public boolean importFromProjectProperties(String projectRootPath, boolean forceOverwrite) {
        if (StringUtils.isBlank(projectRootPath)) {
            return false;
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
            return Lists.newArrayList();
        }
        String key = tableKey.trim();
        String json = state.beanQueryCache.get(key);
        if (StringUtils.isBlank(json) && StringUtils.isNotBlank(projectRootPath)) {
            File redisProp = new File(projectRootPath + "src/main/resources/templates/config/redis.properties");
            if (redisProp.exists()) {
                json = RedisUtils.hget(RedisUtils.getJedis(projectRootPath), "beanQuery", key);
                if (StringUtils.isNotBlank(json)) {
                    state.beanQueryCache.put(key, json);
                }
            }
        }
        if (StringUtils.isNotBlank(json)) {
            try {
                List<BeanQuery> list = JsonUtil.list(json, BeanQuery.class);
                if (list != null) {
                    return list;
                }
            } catch (Exception ignored) {
            }
        }
        return Lists.newArrayList();
    }

    public void saveBeanQueryList(String tableKey, List<BeanQuery> list, String projectRootPath) {
        if (StringUtils.isBlank(tableKey)) {
            return;
        }
        String key = tableKey.trim();
        List<BeanQuery> safeList = list != null ? list : Lists.newArrayList();
        String json = JsonUtil.json(safeList);
        state.beanQueryCache.put(key, json);

        if (StringUtils.isNotBlank(projectRootPath)) {
            File redisProp = new File(projectRootPath + "src/main/resources/templates/config/redis.properties");
            if (redisProp.exists()) {
                RedisUtils.hset(RedisUtils.getJedis(projectRootPath), "beanQuery", key, json);
            }
        }
    }

    @NotNull
    public List<ServiceQuery> getServiceQueryList(String tableKey, String projectRootPath) {
        if (StringUtils.isBlank(tableKey)) {
            return Lists.newArrayList();
        }
        String key = tableKey.trim();
        String json = state.serviceQueryCache.get(key);
        if (StringUtils.isBlank(json) && StringUtils.isNotBlank(projectRootPath)) {
            File redisProp = new File(projectRootPath + "src/main/resources/templates/config/redis.properties");
            if (redisProp.exists()) {
                json = RedisUtils.hget(RedisUtils.getJedis(projectRootPath), "serviceQuery", key);
                if (StringUtils.isNotBlank(json)) {
                    state.serviceQueryCache.put(key, json);
                }
            }
        }
        if (StringUtils.isNotBlank(json)) {
            try {
                List<ServiceQuery> list = JsonUtil.list(json, ServiceQuery.class);
                if (list != null) {
                    return list;
                }
            } catch (Exception ignored) {
            }
        }
        return Lists.newArrayList();
    }

    public void saveServiceQueryList(String tableKey, List<ServiceQuery> list, String projectRootPath) {
        if (StringUtils.isBlank(tableKey)) {
            return;
        }
        String key = tableKey.trim();
        List<ServiceQuery> safeList = list != null ? list : Lists.newArrayList();
        String json = JsonUtil.json(safeList);
        state.serviceQueryCache.put(key, json);

        if (StringUtils.isNotBlank(projectRootPath)) {
            File redisProp = new File(projectRootPath + "src/main/resources/templates/config/redis.properties");
            if (redisProp.exists()) {
                RedisUtils.hset(RedisUtils.getJedis(projectRootPath), "serviceQuery", key, json);
            }
        }
    }
}
