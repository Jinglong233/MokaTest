package com.mokatest.platform.demos.debug;

/**
 * 调试续跑热加载信号异常。
 * 手动暂停期间步骤发生变更时，收到继续命令后抛出该异常，
 * 将执行栈从嵌套容器（循环/条件）中回退到顶层循环，从锚点步骤重新执行。
 * 注意：ForStep/WhileCycleStep 会把子步骤异常包装成 RuntimeException，
 * 因此捕获侧需通过 {@link #isCausedBy(Throwable)} 走 cause 链判断。
 */
public class DebugSessionReloadException extends RuntimeException {

    public DebugSessionReloadException(String message) {
        super(message);
    }

    /**
     * 判断异常（或其 cause 链）是否包含热加载信号。
     */
    public static boolean isCausedBy(Throwable e) {
        while (e != null) {
            if (e instanceof DebugSessionReloadException) {
                return true;
            }
            e = e.getCause();
        }
        return false;
    }
}
