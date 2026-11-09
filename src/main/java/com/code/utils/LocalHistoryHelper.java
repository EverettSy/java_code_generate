package com.code.utils;

import com.intellij.history.LocalHistory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 生成历史快照与一键事务级回滚管理器（v3.0 M3：集成 IDEA LocalHistory 系统标签与内存快照栈）
 *
 * 核心能力：
 * 1. 每次批量生成前自动向 IntelliJ LocalHistory 打上系统级标签（Label），方便开发者在 IDE Local History 视图中追溯。
 * 2. 自动记录本次批量生成涉及的所有文件在写入前的原始快照（已存在文件保存原文，新文件标记为新增）。
 * 3. 支持一键回滚最近一次生成批次（恢复被合并/覆盖的文件原文，并自动清理本次新增的文件与空目录）。
 *
 * @author syrobin
 */
public final class LocalHistoryHelper {

    public static class FileSnapshot {
        private final String absolutePath;
        private final boolean existedBefore;
        private final String previousContent;

        public FileSnapshot(String absolutePath, boolean existedBefore, @Nullable String previousContent) {
            this.absolutePath = absolutePath;
            this.existedBefore = existedBefore;
            this.previousContent = previousContent;
        }

        public String getAbsolutePath() {
            return absolutePath;
        }

        public boolean isExistedBefore() {
            return existedBefore;
        }

        @Nullable
        public String getPreviousContent() {
            return previousContent;
        }
    }

    public static class BatchSnapshot {
        private final String summary;
        private final long timestamp;
        private final Map<String, FileSnapshot> fileSnapshots = new LinkedHashMap<>();

        public BatchSnapshot(String summary) {
            this.summary = StringUtils.defaultIfBlank(summary, "Batch Code Generation");
            this.timestamp = System.currentTimeMillis();
        }

        public String getSummary() {
            return summary;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public Map<String, FileSnapshot> getFileSnapshots() {
            return fileSnapshots;
        }
    }

    private static final String DEFAULT_PROJECT_KEY = "__DEFAULT_HEADLESS_PROJECT__";
    private static final Map<String, Deque<BatchSnapshot>> HISTORY_STACKS = new ConcurrentHashMap<>();
    private static final Map<String, BatchSnapshot> ACTIVE_BATCHES = new ConcurrentHashMap<>();

    private LocalHistoryHelper() {
    }

    private static String keyFor(@Nullable Project project) {
        if (project == null) {
            return DEFAULT_PROJECT_KEY;
        }
        return StringUtils.defaultIfBlank(project.getLocationHash(), project.getName());
    }

    /**
     * 开启一个新的生成批次，并向 IDEA LocalHistory 写入系统标签
     */
    public static void startGenerationBatch(@Nullable Project project, @NotNull String summary) {
        String key = keyFor(project);
        ACTIVE_BATCHES.put(key, new BatchSnapshot(summary));

        if (project != null && !project.isDisposed() && ApplicationManager.getApplication() != null) {
            try {
                LocalHistory.getInstance().putSystemLabel(project, "Syrobin Code Generate — " + summary);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * 在文件写入/合并前记录其原始状态（同一批次内对同一文件仅记录首次写入前的状态）
     */
    public static void recordFileBeforeWrite(@Nullable Project project, @NotNull String targetFilePath) {
        if (StringUtils.isBlank(targetFilePath)) {
            return;
        }
        String key = keyFor(project);
        BatchSnapshot active = ACTIVE_BATCHES.computeIfAbsent(key, k -> new BatchSnapshot("Auto Batch"));
        String normPath = new File(targetFilePath).getAbsolutePath();
        if (active.getFileSnapshots().containsKey(normPath)) {
            return;
        }

        File file = new File(normPath);
        if (file.exists() && file.isFile()) {
            try {
                String oldContent = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                active.getFileSnapshots().put(normPath, new FileSnapshot(normPath, true, oldContent));
            } catch (Exception ignored) {
            }
        } else {
            active.getFileSnapshots().put(normPath, new FileSnapshot(normPath, false, null));
        }
    }

    /**
     * 结束当前生成批次并压入可回滚历史栈
     */
    public static void finishGenerationBatch(@Nullable Project project) {
        String key = keyFor(project);
        BatchSnapshot finished = ACTIVE_BATCHES.remove(key);
        if (finished != null && !finished.getFileSnapshots().isEmpty()) {
            Deque<BatchSnapshot> stack = HISTORY_STACKS.computeIfAbsent(key, k -> new ArrayDeque<>());
            stack.push(finished);
            while (stack.size() > 20) {
                stack.removeLast();
            }
        }
    }

    /**
     * 判断当前项目是否存在可回滚的生成批次
     */
    public static boolean hasRollbackableBatch(@Nullable Project project) {
        String key = keyFor(project);
        BatchSnapshot active = ACTIVE_BATCHES.get(key);
        if (active != null && !active.getFileSnapshots().isEmpty()) {
            return true;
        }
        Deque<BatchSnapshot> stack = HISTORY_STACKS.get(key);
        return stack != null && !stack.isEmpty();
    }

    /**
     * 获取最近一次可回滚批次的摘要说明
     */
    @Nullable
    public static BatchSnapshot peekLastBatch(@Nullable Project project) {
        String key = keyFor(project);
        BatchSnapshot active = ACTIVE_BATCHES.get(key);
        if (active != null && !active.getFileSnapshots().isEmpty()) {
            return active;
        }
        Deque<BatchSnapshot> stack = HISTORY_STACKS.get(key);
        return (stack != null && !stack.isEmpty()) ? stack.peek() : null;
    }

    /**
     * 一键回滚最近一次代码生成批次（恢复被修改文件，删除新增文件），返回回滚的文件数量
     */
    public static int rollbackLastBatch(@Nullable Project project) throws Exception {
        String key = keyFor(project);
         finishGenerationBatch(project);
        Deque<BatchSnapshot> stack = HISTORY_STACKS.get(key);
        if (stack == null || stack.isEmpty()) {
            return 0;
        }

        BatchSnapshot batch = stack.pop();
        int count = 0;
        Set<File> touchedRoots = new LinkedHashSet<>();

        for (FileSnapshot snap : batch.getFileSnapshots().values()) {
            File f = new File(snap.getAbsolutePath());
            if (snap.isExistedBefore()) {
                if (snap.getPreviousContent() != null) {
                    CodeGenerateUtils.writeFile(snap.getPreviousContent(), f.getAbsolutePath());
                    count++;
                }
            } else {
                if (f.exists() && f.delete()) {
                    count++;
                    cleanEmptyParentDirs(f.getParentFile());
                }
            }
            if (f.getParentFile() != null) {
                touchedRoots.add(f.getParentFile());
            }
        }

        if (project != null && !project.isDisposed() && ApplicationManager.getApplication() != null) {
            try {
                LocalHistory.getInstance().putSystemLabel(project, "Rollback Syrobin Code Generate — " + batch.getSummary());
                for (File dir : touchedRoots) {
                    VirtualFile vf = VfsUtil.findFileByIoFile(dir, true);
                    if (vf != null) {
                        VfsUtil.markDirtyAndRefresh(true, true, true, vf);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return count;
    }

    private static void cleanEmptyParentDirs(@Nullable File dir) {
        int depth = 0;
        File current = dir;
        while (current != null && current.exists() && current.isDirectory() && depth < 5) {
            String[] children = current.list();
            if (children != null && children.length == 0) {
                File parent = current.getParentFile();
                if (!current.delete()) {
                    break;
                }
                current = parent;
                depth++;
            } else {
                break;
            }
        }
    }

    public static void clearHistory(@Nullable Project project) {
        String key = keyFor(project);
        ACTIVE_BATCHES.remove(key);
        HISTORY_STACKS.remove(key);
    }
}
