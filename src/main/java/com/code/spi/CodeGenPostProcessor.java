package com.code.spi;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 开放生态代码生成后置扩展点 SPI（v4.0 M3）
 *
 * 核心能力：
 * 1. 允许第三方 IDEA 插件通过 {@code com.syrobin.java.code.generate.postProcessor} 扩展点介入代码生成流水线。
 * 2. 允许团队自定义脚本或单元测试通过 {@link #registerProcessor(CodeGenPostProcessor)} 动态挂载后置处理器（例如：自动注入自定义文件头、生成权限 SQL、追加 GraphQL Schema 等）。
 *
 * @author syrobin
 */
public interface CodeGenPostProcessor {

    ExtensionPointName<CodeGenPostProcessor> EP_NAME =
            ExtensionPointName.create("com.syrobin.java.code.generate.postProcessor");

    List<CodeGenPostProcessor> PROGRAMMATIC_PROCESSORS = new CopyOnWriteArrayList<>();

    /**
     * 当单张表的所有目标文件在内存中渲染完成后调用，可直接修改或追加 {@code renderedFiles} 中的内容。
     *
     * @param project       当前 IntelliJ 项目实例（无头测试下可能为 null）
     * @param tableName     当前数据库表名
     * @param templateModel 传递给 FreeMarker 模板的完整上下文模型
     * @param renderedFiles 「目标文件绝对路径 -> 渲染后的文件源码」映射（可读写）
     */
    void afterRenderFiles(@Nullable Project project,
                          @NotNull String tableName,
                          @NotNull Map<String, Object> templateModel,
                          @NotNull Map<String, String> renderedFiles);

    static void registerProcessor(@NotNull CodeGenPostProcessor processor) {
        if (!PROGRAMMATIC_PROCESSORS.contains(processor)) {
            PROGRAMMATIC_PROCESSORS.add(processor);
        }
    }

    static void unregisterProcessor(@NotNull CodeGenPostProcessor processor) {
        PROGRAMMATIC_PROCESSORS.remove(processor);
    }

    static void clearProgrammaticProcessors() {
        PROGRAMMATIC_PROCESSORS.clear();
    }

    static void invokeAll(@Nullable Project project,
                          @NotNull String tableName,
                          @NotNull Map<String, Object> templateModel,
                          @NotNull Map<String, String> renderedFiles) {
        for (CodeGenPostProcessor processor : PROGRAMMATIC_PROCESSORS) {
            try {
                processor.afterRenderFiles(project, tableName, templateModel, renderedFiles);
            } catch (Throwable ignored) {
            }
        }
        try {
            for (CodeGenPostProcessor ep : EP_NAME.getExtensionList()) {
                ep.afterRenderFiles(project, tableName, templateModel, renderedFiles);
            }
        } catch (Throwable ignored) {
        }
    }
}
