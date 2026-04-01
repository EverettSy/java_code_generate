package com.code.utils;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

/**
 * @ClassName MyNotifier
 * @Description 消息通知工具类（兼容 IDEA 2023+ ~ 2026+）
 * @Author 孙凯伦
 */
public class MyNotifier {

    private static final String GROUP_ID = "SklCodeGenerateNotification";

    /**
     * 错误通知
     */
    public static void notifyError(@Nullable Project project, String content) {
        ApplicationManager.getApplication().invokeLater(() ->
                NotificationGroupManager.getInstance()
                        .getNotificationGroup(GROUP_ID)
                        .createNotification(content, NotificationType.ERROR)
                        .notify(project)
        );
    }

    /**
     * 信息通知
     */
    public static void notifyInformation(@Nullable Project project, String content) {
        ApplicationManager.getApplication().invokeLater(() ->
                NotificationGroupManager.getInstance()
                        .getNotificationGroup(GROUP_ID)
                        .createNotification(content, NotificationType.INFORMATION)
                        .notify(project)
        );
    }
}
