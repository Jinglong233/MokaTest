package com.mokatest.platform.demos.ai.config;

/**
 * AI 调用场景上下文（ThreadLocal）：调用方在进入业务场景时标记 scene/projectId/userId，
 * AiUsageListener 写用量日志时读取（listener attributes 无法跨 AiServices 传递业务上下文）。
 *
 * 用法：AiUsageContext.withScene("GENERATE_CASE", projectId, () -> { ...模型调用... });
 */
public final class AiUsageContext {

    private AiUsageContext() {
    }

    private record CallContext(String scene, Integer projectId, String userId) {
    }

    private static final ThreadLocal<CallContext> CURRENT = new ThreadLocal<>();

    public static void set(String scene, Integer projectId, String userId) {
        CURRENT.set(new CallContext(scene, projectId, userId));
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static String scene() {
        CallContext c = CURRENT.get();
        return c != null ? c.scene() : null;
    }

    public static Integer projectId() {
        CallContext c = CURRENT.get();
        return c != null ? c.projectId() : null;
    }

    public static String userId() {
        CallContext c = CURRENT.get();
        return c != null ? c.userId() : null;
    }

    /** 在指定场景下执行（自动清理，可嵌套——嵌套时内层覆盖外层，结束后恢复外层） */
    public static <T> T withScene(String scene, Integer projectId, java.util.function.Supplier<T> action) {
        CallContext prev = CURRENT.get();
        set(scene, projectId, currentUserId());
        try {
            return action.get();
        } finally {
            if (prev != null) {
                CURRENT.set(prev);
            } else {
                clear();
            }
        }
    }

    public static void withScene(String scene, Integer projectId, Runnable action) {
        withScene(scene, projectId, () -> {
            action.run();
            return null;
        });
    }

    /** 当前登录用户（计划执行等无线程登录上下文时返回 null，不抛异常） */
    private static String currentUserId() {
        try {
            return cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
        } catch (Exception e) {
            return null;
        }
    }
}
