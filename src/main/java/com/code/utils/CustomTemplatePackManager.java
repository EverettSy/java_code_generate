package com.code.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 开放生态自定义模板包（Custom Template Pack）管理器（v4.0 M3）
 *
 * 核心能力：
 * 1. 允许用户/架构师在内置 20 套模板之外，定义任意数量的自定义 FreeMarker 模板及输出路径规则（如 DDD Repository、GraphQL Schema、权限菜单 SQL、Dockerfile 等）。
 * 2. 支持将自定义模板包导出为 `.syrobin-template-pack.json` 或从磁盘导入，实现跨项目/跨团队的脚手架生态共享。
 *
 * @author syrobin
 */
public final class CustomTemplatePackManager {

    public static final String DEFAULT_PACK_FILENAME = ".syrobin-template-pack.json";

    private CustomTemplatePackManager() {
    }

    /**
     * 单个自定义模板定义项
     */
    public static class CustomTemplateDefinition {
        private String name;
        private String description;
        /**
         * 输出相对路径模式，支持占位符：
         * ${packageRelDir}、${ClassName}、${className}、${tableName}、${moduleName}
         * 例如："src/main/resources/graphql/${className}.graphqls"
         */
        private String outputPathPattern;
        private String layerKey = "controller";
        private String templateContent;
        private boolean enabled = true;

        public CustomTemplateDefinition() {
        }

        public CustomTemplateDefinition(String name,
                                        String description,
                                        String outputPathPattern,
                                        String layerKey,
                                        String templateContent,
                                        boolean enabled) {
            this.name = name;
            this.description = description;
            this.outputPathPattern = outputPathPattern;
            this.layerKey = StringUtils.defaultIfBlank(layerKey, "controller");
            this.templateContent = templateContent;
            this.enabled = enabled;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getOutputPathPattern() {
            return outputPathPattern;
        }

        public void setOutputPathPattern(String outputPathPattern) {
            this.outputPathPattern = outputPathPattern;
        }

        public String getLayerKey() {
            return layerKey;
        }

        public void setLayerKey(String layerKey) {
            this.layerKey = layerKey;
        }

        public String getTemplateContent() {
            return templateContent;
        }

        public void setTemplateContent(String templateContent) {
            this.templateContent = templateContent;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * 模板包容器模型
     */
    public static class TemplatePack {
        private String packName = "Syrobin Custom Template Pack";
        private String packVersion = "4.0";
        private String author = "syrobin";
        private List<CustomTemplateDefinition> templates = new ArrayList<>();

        public String getPackName() {
            return packName;
        }

        public void setPackName(String packName) {
            this.packName = packName;
        }

        public String getPackVersion() {
            return packVersion;
        }

        public void setPackVersion(String packVersion) {
            this.packVersion = packVersion;
        }

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public List<CustomTemplateDefinition> getTemplates() {
            return templates;
        }

        public void setTemplates(List<CustomTemplateDefinition> templates) {
            this.templates = templates;
        }
    }

    /**
     * 解析自定义模板的目标文件绝对路径
     */
    @NotNull
    public static String resolveTargetFilePath(@NotNull CustomTemplateDefinition def,
                                               @NotNull String layerRootPath,
                                               @NotNull String packageRelDir,
                                               @NotNull Map<String, Object> templateModel) {
        String pattern = StringUtils.defaultIfBlank(def.getOutputPathPattern(), "src/main/resources/custom/${ClassName}.txt");
        String resolvedRel = pattern
                .replace("${packageRelDir}", packageRelDir)
                .replace("${ClassName}", Objects.toString(templateModel.get("ClassName"), "Entity"))
                .replace("${className}", Objects.toString(templateModel.get("className"), "entity"))
                .replace("${tableName}", Objects.toString(templateModel.get("tableName"), "table"))
                .replace("${moduleName}", Objects.toString(templateModel.get("moduleName"), "module"));
        resolvedRel = resolvedRel.replace('\\', '/').replaceAll("^/+", "");
        return CodeGenerateUtils.normalizeDirPath(layerRootPath) + resolvedRel;
    }

    /**
     * 导出自定义模板包到指定 JSON 文件
     */
    @NotNull
    public static File exportTemplatePack(@NotNull File targetFile, @NotNull TemplatePack pack) throws Exception {
        if (targetFile.getParentFile() != null && !targetFile.getParentFile().exists()) {
            targetFile.getParentFile().mkdirs();
        }
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        String json = mapper.writeValueAsString(pack);
        Files.writeString(targetFile.toPath(), json, StandardCharsets.UTF_8);
        return targetFile;
    }

    /**
     * 从指定 JSON 文件导入自定义模板包
     */
    @NotNull
    public static TemplatePack importTemplatePack(@NotNull File sourceFile) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        TemplatePack pack = mapper.readValue(sourceFile, TemplatePack.class);
        return pack != null ? pack : new TemplatePack();
    }
}
