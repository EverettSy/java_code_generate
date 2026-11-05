package com.code.utils;

import com.code.config.CodeGenSettingsState;
import com.code.utils.entity.*;
import com.code.utils.freemarker.FreeMarkers;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.FileTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.StringTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Created with IntelliJ IDEA.
 * User:    syrobin
 * Date:    2016/2/1
 * Time:    14:40
 * details: 核心代码生成器（P3：支持离线 DDL SQL 解析生成、字段校验注解智能推断、多数据库方言与增量合并）
 */
public class CodeGenerate {

    private static <T> T supplyOnEdt(Supplier<T> supplier) {
        if (ApplicationManager.getApplication() == null || ApplicationManager.getApplication().isDispatchThread()) {
            return supplier.get();
        }
        AtomicReference<T> resultRef = new AtomicReference<>();
        ApplicationManager.getApplication().invokeAndWait(() -> resultRef.set(supplier.get()));
        return resultRef.get();
    }

    private static boolean isEnabled(Map<String, Object> parameterMap, String key) {
        return StringUtils.equalsIgnoreCase(String.valueOf(parameterMap.get(key)), "y");
    }

    /**
     * 兼容旧版调用签名
     */
    public static void foundCode(String table, String moduleName, String author, Map<String, Object> parameterMap, String path) throws Exception {
        foundCode(null, table, moduleName, author, parameterMap, path);
    }

    /**
     * 核心代码生成方法（支持在线数据库元数据读取与离线 DDL SQL 解析双模式）
     */
    @SuppressWarnings("unchecked")
    public static void foundCode(@Nullable Project project, String table, String moduleName, String author, Map<String, Object> parameterMap, String path) throws Exception {
        if (StringUtils.isBlank(table)) {
            return;
        }

        String basePackage = resolveBasePackageName(project, path);
        String packageName = basePackage + "." + moduleName;
        IncrementalCodeMerger.WriteStrategy writeStrategy = resolveWriteStrategy(project, parameterMap);

        String tableComment = null;
        List<Table> list = null;

        // 1. 优先检查是否存在离线 DDL 解析出的表元数据
        Object ddlMapObj = parameterMap.get("parsedDdlTables");
        if (ddlMapObj instanceof Map<?, ?>) {
            Map<String, DdlSqlParser.ParsedTable> ddlMap = (Map<String, DdlSqlParser.ParsedTable>) ddlMapObj;
            DdlSqlParser.ParsedTable parsed = findParsedTableIgnoreCase(ddlMap, table);
            if (parsed != null) {
                table = parsed.getTableName();
                tableComment = Objects.toString(parsed.getTableComment(), "");
                list = parsed.getColumns();
            }
        }

        // 2. 若未使用离线 DDL，则通过数据库连接拉取表注释与字段元数据
        if (list == null) {
            String database = resolveDatabaseName(project, path);
            try (Connection connection = CodeGenerateUtils.getDblink(project, path)) {
                DatabaseDialect dialect = CodeGenerateUtils.detectDialect(connection);
                tableComment = dialect.querySingleTableComment(connection, database, table);
                if (tableComment == null) {
                    throw new IllegalArgumentException("未在数据库 [" + database + "] 中找到表：" + table);
                }
                list = dialect.queryColumns(connection, database, table);
            }
        }

        boolean enableValidation = resolveEnableValidation(project, parameterMap);
        ValidationAnnotationResolver.enrichValidationAnnotations(list, enableValidation);

        String bootVersion = resolveBootVersion(project, parameterMap);
        String validationPkg = "3".equals(bootVersion) ? "jakarta.validation.constraints" : "javax.validation.constraints";

        String className = CodeGenerateUtils.toUpper(table);
        String address = path + "/src/main/java/" + packageName;
        Configuration cfg = buildFreemarkerConfiguration(project, path);

        Map<String, Object> model = Maps.newHashMap();
        model.put("packageName", StringUtils.lowerCase(packageName));
        model.put("className", StringUtils.uncapitalize(className));
        model.put("ClassName", className);
        model.put("author", author);
        model.put("date", CodeGenerateUtils.getDate());
        model.put("functionName", Objects.toString(tableComment, ""));
        model.put("tableName", table);
        model.put("bootVersion", bootVersion);
        model.put("docStyle", resolveDocStyle(project, parameterMap));
        model.put("enableValidation", enableValidation);
        model.put("validationPkg", validationPkg);
        model.put("list", list);
        model.put("braceL", "{");
        model.put("braceR", "}");
        model.put("serviceQueryList", parameterMap.getOrDefault("serviceQueryList", Lists.newArrayList()));
        model.put("beanQuery", parameterMap.getOrDefault("beanQuery", Lists.newArrayList()));

        String basePackageDir = address.replace(".", "/");

        // 1. 生成 Mapper
        if (isEnabled(parameterMap, "mapper")) {
            renderAndWrite(project, cfg, "skl_mapper.java.ftl", model,
                    basePackageDir + "/mapper/" + className + "Mapper.java", writeStrategy);
        }

        // 2. 解析字段枚举注释并按需生成状态枚举类
        List<EnumsEntityName> enumsList = Lists.newArrayList();
        for (Table t : list) {
            if (parameterMap.get("fixedEnums") != null
                    && !Objects.equals(t.getTypeName(), parameterMap.get("fixedEnums"))) {
                continue;
            }
            List<EnumsEntityContent> enumsEntitiesList = EnumCommentParser.parseEnumItems(t.getColumnComment());
            if (!enumsEntitiesList.isEmpty()) {
                EnumsEntityName enumName = new EnumsEntityName();
                enumName.setSmallName(t.getColumnName());
                enumName.setBigName(t.getColumnNameUpper());
                enumsList.add(enumName);

                model.put("enumsEntityName", t.getColumnNameUpper());
                model.put("enumsEntitiesList", enumsEntitiesList);

                if (isEnabled(parameterMap, "enums")) {
                    renderAndWrite(project, cfg, "skl_entity_enums.java.ftl", model,
                            basePackageDir + "/entity/enums/" + className + t.getColumnNameUpper() + "Enum.java", writeStrategy);
                }
            }
        }
        model.put("enumsList", enumsList);

        // 3. 生成实体类 Entity
        if (isEnabled(parameterMap, "entity")) {
            renderAndWrite(project, cfg, "skl_entity.java.ftl", model,
                    basePackageDir + "/entity/" + className + ".java", writeStrategy);
        }

        // 4. 生成 VO 类
        if (isEnabled(parameterMap, "entityVO")) {
            renderAndWrite(project, cfg, "skl_entity_vo.java.ftl", model,
                    basePackageDir + "/entity/vo/" + className + "VO.java", writeStrategy);
        }

        // 5. 生成 BO 类
        if (isEnabled(parameterMap, "entityBO")) {
            renderAndWrite(project, cfg, "skl_entity_bo.java.ftl", model,
                    basePackageDir + "/entity/bo/" + className + "BO.java", writeStrategy);
        }

        // 6. 生成 Query 类
        if (isEnabled(parameterMap, "entityQuery")) {
            renderAndWrite(project, cfg, "skl_entity_query.java.ftl", model,
                    basePackageDir + "/entity/query/" + className + "Query.java", writeStrategy);
        }

        // 7. 生成 Service 接口与实现类
        if (isEnabled(parameterMap, "service")) {
            renderAndWrite(project, cfg, "skl_service.java.ftl", model,
                    basePackageDir + "/service/" + className + "Service.java", writeStrategy);
            renderAndWrite(project, cfg, "skl_serviceImpl.java.ftl", model,
                    basePackageDir + "/service/impl/" + className + "ServiceImpl.java", writeStrategy);
        }

        // 8. 生成 ServiceQuery 类
        if (isEnabled(parameterMap, "serviceQuery")) {
            renderAndWrite(project, cfg, "skl_service_query.java.ftl", model,
                    basePackageDir + "/service/query/" + className + "ServiceQuery.java", writeStrategy);
        }

        // 9. 生成 ServiceResults 类
        if (isEnabled(parameterMap, "serviceResults")) {
            renderAndWrite(project, cfg, "skl_service_results.java.ftl", model,
                    basePackageDir + "/service/results/" + className + "ServiceResults.java", writeStrategy);
        }

        // 10. 生成 Controller 类
        if (isEnabled(parameterMap, "controller")) {
            renderAndWrite(project, cfg, "skl_controller.java.ftl", model,
                    basePackageDir + "/controller/" + className + "Controller.java", writeStrategy);
        }
    }

    private static DdlSqlParser.ParsedTable findParsedTableIgnoreCase(Map<String, DdlSqlParser.ParsedTable> ddlMap, String table) {
        if (ddlMap == null || table == null) {
            return null;
        }
        DdlSqlParser.ParsedTable direct = ddlMap.get(table);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, DdlSqlParser.ParsedTable> entry : ddlMap.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(table)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static Configuration buildFreemarkerConfiguration(@Nullable Project project, String path) throws Exception {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_29);
        cfg.setDefaultEncoding("UTF-8");

        List<TemplateLoader> loaders = new ArrayList<>();

        if (project != null) {
            CodeGenSettingsState.ConfigState st = CodeGenSettingsState.getInstance(project).getState();
            if (st != null && st.customTemplates != null && !st.customTemplates.isEmpty()) {
                StringTemplateLoader stringLoader = new StringTemplateLoader();
                for (Map.Entry<String, String> entry : st.customTemplates.entrySet()) {
                    if (StringUtils.isNoneBlank(entry.getKey(), entry.getValue())) {
                        stringLoader.putTemplate(entry.getKey(), entry.getValue());
                    }
                }
                loaders.add(stringLoader);
            }
        }

        File projectTemplateDir = new File(path + "src/main/resources/templates/");
        if (projectTemplateDir.exists() && projectTemplateDir.isDirectory()) {
            loaders.add(new FileTemplateLoader(projectTemplateDir));
        }

        loaders.add(new ClassTemplateLoader(CodeGenerate.class, "/templates"));

        cfg.setTemplateLoader(new MultiTemplateLoader(loaders.toArray(new TemplateLoader[0])));
        return cfg;
    }

    private static boolean resolveEnableValidation(@Nullable Project project, Map<String, Object> parameterMap) {
        Object valObj = parameterMap.get("enableValidation");
        if (valObj instanceof Boolean) {
            return (Boolean) valObj;
        }
        if (project != null) {
            CodeGenSettingsState.ConfigState st = CodeGenSettingsState.getInstance(project).getState();
            if (st != null) {
                return st.enableValidation;
            }
        }
        return true;
    }

    private static IncrementalCodeMerger.WriteStrategy resolveWriteStrategy(@Nullable Project project, Map<String, Object> parameterMap) {
        Object paramStrategy = parameterMap.get("writeStrategy");
        if (paramStrategy != null) {
            return IncrementalCodeMerger.WriteStrategy.fromName(String.valueOf(paramStrategy));
        }
        if (project != null) {
            CodeGenSettingsState.ConfigState st = CodeGenSettingsState.getInstance(project).getState();
            if (st != null && StringUtils.isNotBlank(st.writeStrategy)) {
                return IncrementalCodeMerger.WriteStrategy.fromName(st.writeStrategy);
            }
        }
        return IncrementalCodeMerger.WriteStrategy.INCREMENTAL_MERGE;
    }

    private static String resolveBootVersion(@Nullable Project project, Map<String, Object> parameterMap) {
        Object paramBoot = parameterMap.get("bootVersion");
        if (paramBoot != null && StringUtils.isNotBlank(String.valueOf(paramBoot))) {
            return String.valueOf(paramBoot).trim();
        }
        if (project != null) {
            CodeGenSettingsState.ConfigState st = CodeGenSettingsState.getInstance(project).getState();
            if (st != null && StringUtils.isNotBlank(st.bootVersion)) {
                return st.bootVersion.trim();
            }
        }
        return "2";
    }

    private static String resolveDocStyle(@Nullable Project project, Map<String, Object> parameterMap) {
        Object paramDoc = parameterMap.get("docStyle");
        if (paramDoc != null && StringUtils.isNotBlank(String.valueOf(paramDoc))) {
            return String.valueOf(paramDoc).trim();
        }
        if (project != null) {
            CodeGenSettingsState.ConfigState st = CodeGenSettingsState.getInstance(project).getState();
            if (st != null && StringUtils.isNotBlank(st.docStyle)) {
                return st.docStyle.trim();
            }
        }
        return "NONE";
    }

    private static String resolveDatabaseName(@Nullable Project project, String path) throws Exception {
        if (project != null) {
            CodeGenSettingsState.ConfigState st = CodeGenSettingsState.getInstance(project).getState();
            if (st != null && StringUtils.isNotBlank(st.database)) {
                return st.database.trim();
            }
            if (st != null && StringUtils.isNotBlank(st.jdbcUrl)) {
                String extracted = DatabaseDialect.extractDatabaseFromJdbcUrl(st.jdbcUrl);
                if (StringUtils.isNotBlank(extracted)) {
                    return extracted;
                }
            }
        }
        File propFile = new File(path + "src/main/resources/templates/config/mysql.properties");
        if (propFile.exists()) {
            return PropertiesUtil.getMySqlProperties(path).getProperty("database");
        }
        throw new IllegalStateException("未配置数据库名称 (database)，请在生成向导「3. 数据源设置」中填写或使用「4. 离线 DDL SQL 解析」。");
    }

    private static String resolveBasePackageName(@Nullable Project project, String path) throws Exception {
        if (project != null) {
            CodeGenSettingsState.ConfigState st = CodeGenSettingsState.getInstance(project).getState();
            if (st != null && StringUtils.isNotBlank(st.packageName)) {
                return st.packageName.trim();
            }
        }
        File propFile = new File(path + "src/main/resources/templates/config/mysql.properties");
        if (propFile.exists()) {
            return PropertiesUtil.getMySqlProperties(path).getProperty("packageName");
        }
        return "com.example";
    }

    private static void renderAndWrite(@Nullable Project project,
                                       Configuration cfg,
                                       String templateName,
                                       Map<String, Object> model,
                                       String targetFile,
                                       IncrementalCodeMerger.WriteStrategy strategy) throws Exception {
        Template template = cfg.getTemplate(templateName, "UTF-8");
        String content = FreeMarkers.renderTemplate(template, model);
        IncrementalCodeMerger.writeWithStrategy(project, content, targetFile, strategy);
    }

    public static void fixedEnums(Map<String, Object> parameterMap) {
        if (isEnabled(parameterMap, "enums")) {
            String fieldName = supplyOnEdt(() -> Messages.showInputDialog(
                    "枚举字段名称（不填写全部生成，填写只生成填写字段）",
                    "是否设置对应枚举类",
                    Messages.getInformationIcon()
            ));
            if (StringUtils.isNotBlank(fieldName)) {
                parameterMap.put("fixedEnums", fieldName.trim());
            }
        }
    }

    public static void beanQuery(Map<String, Object> parameterMap, String tableName, String path) {
        String hget = RedisUtils.hget(RedisUtils.getJedis(path), "beanQuery", tableName);
        List<BeanQuery> mapList = StringUtils.isNotBlank(hget) ? JsonUtil.list(hget, BeanQuery.class) : Lists.newArrayList();
        if (mapList == null) {
            mapList = Lists.newArrayList();
        }
        parameterMap.put("beanQuery", mapList);
    }

    public static void serviceQuery(Map<String, Object> parameterMap, String tableName, String path) {
        String hget = RedisUtils.hget(RedisUtils.getJedis(path), "serviceQuery", tableName);
        List<ServiceQuery> mapList = StringUtils.isNotBlank(hget) ? JsonUtil.list(hget, ServiceQuery.class) : Lists.newArrayList();
        if (mapList == null) {
            mapList = Lists.newArrayList();
        }
        parameterMap.put("serviceQueryList", mapList);
    }
}
