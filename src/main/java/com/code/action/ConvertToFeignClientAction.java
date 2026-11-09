package com.code.action;

import com.code.config.CodeGenSettingsState;
import com.code.utils.FeignClientConverter;
import com.code.utils.IncrementalCodeMerger;
import com.code.utils.LocalHistoryHelper;
import com.code.utils.MyNotifier;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 将已有 Controller / Service / API 接口直接一键转换为 Spring Cloud @FeignClient 接口 Action
 *
 * @author syrobin
 */
public class ConvertToFeignClientAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        VirtualFile[] vFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (vFiles == null || vFiles.length == 0) {
            VirtualFile single = e.getData(CommonDataKeys.VIRTUAL_FILE);
            if (single != null) {
                vFiles = new VirtualFile[]{single};
            }
        }
        if (vFiles == null || vFiles.length == 0) {
            MyNotifier.notifyError(project, "请先在工程树或编辑器中选中要转换的 Controller 或接口 (.java) 文件");
            return;
        }

        List<File> javaFiles = new ArrayList<>();
        for (VirtualFile vf : vFiles) {
            collectJavaFiles(new File(vf.getPath()), javaFiles);
        }

        if (javaFiles.isEmpty()) {
            MyNotifier.notifyError(project, "未在所选路径中找到可转换的 .java 接口或 Controller 文件");
            return;
        }

        CodeGenSettingsState.ConfigState st = CodeGenSettingsState.getInstance(project).getState();
        String defaultServiceName = (st != null && StringUtils.isNotBlank(st.feignServiceName))
                ? st.feignServiceName
                : "";

        String inputServiceName = Messages.showInputDialog(
                project,
                "请输入目标微服务名称 (@FeignClient name 属性，留空则按模块名自动推断为 xxx-service)：",
                "将接口/Controller 转为 Feign Client",
                Messages.getQuestionIcon(),
                defaultServiceName,
                null
        );
        if (inputServiceName == null) {
            return;
        }
        if (st != null && StringUtils.isNotBlank(inputServiceName)) {
            st.feignServiceName = inputServiceName.trim();
        }

        IncrementalCodeMerger.WriteStrategy strategy = (st != null && StringUtils.isNotBlank(st.writeStrategy))
                ? IncrementalCodeMerger.WriteStrategy.fromName(st.writeStrategy)
                : IncrementalCodeMerger.WriteStrategy.INCREMENTAL_MERGE;

        LocalHistoryHelper.startGenerationBatch(project, "ConvertToFeignClient(" + javaFiles.size() + ")");
        int convertedCount = 0;
        List<File> generatedTargets = new ArrayList<>();
        try {
            for (File srcFile : javaFiles) {
                if (srcFile.getName().endsWith("FeignClient.java")) {
                    continue;
                }
                String srcCode = Files.readString(srcFile.toPath(), StandardCharsets.UTF_8);
                String feignCode = FeignClientConverter.convertToFeignClientSource(
                        srcCode,
                        inputServiceName.trim(),
                        null
                );
                File targetFile = FeignClientConverter.resolveTargetFeignFile(srcFile);
                LocalHistoryHelper.recordFileBeforeWrite(project, targetFile.getAbsolutePath());
                IncrementalCodeMerger.writeWithStrategy(project, feignCode, targetFile.getAbsolutePath(), strategy);
                generatedTargets.add(targetFile);
                convertedCount++;
            }
            LocalHistoryHelper.finishGenerationBatch(project);

            for (File target : generatedTargets) {
                VirtualFile vf = VfsUtil.findFileByIoFile(target.getParentFile(), true);
                if (vf != null) {
                    VfsUtil.markDirtyAndRefresh(true, true, true, vf);
                }
            }

            if (convertedCount > 0) {
                MyNotifier.notifyInformation(project, "已成功将 " + convertedCount + " 个接口/Controller 转换为 FeignClient！（支持在向导或侧边栏一键回滚）");
            } else {
                MyNotifier.notifyWarning(project, "未找到需要转换的 Controller 或接口文件");
            }
        } catch (Exception ex) {
            MyNotifier.notifyError(project, "转换 FeignClient 失败：" + ex.getMessage());
        } finally {
            LocalHistoryHelper.finishGenerationBatch(project);
        }
    }

    private void collectJavaFiles(File file, List<File> out) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isFile() && file.getName().endsWith(".java")) {
            out.add(file);
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    collectJavaFiles(child, out);
                }
            }
        }
    }
}
