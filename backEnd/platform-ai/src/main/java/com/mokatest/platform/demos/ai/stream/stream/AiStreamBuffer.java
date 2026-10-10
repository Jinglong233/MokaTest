package com.mokatest.platform.demos.ai.stream;

/**
 * 单会话流式生成缓冲（内存态）
 *
 * 生成期间：边推 SSE 边累积内容；重连时先回放再接实时流。
 * 生成结束后保留一段时间供重开放映，由注册表按 TTL 清理。
 */
public class AiStreamBuffer {

    /** 会话状态 */
    public enum State { STREAMING, DONE, FAILED, STOPPED }

    private final StringBuilder content = new StringBuilder();
    private volatile State state = State.STREAMING;
    private volatile String resultJson;
    private volatile String errorMsg;
    private volatile boolean cancelRequested;
    private volatile Runnable cancelHandle;
    private final long createTime = System.currentTimeMillis();
    private volatile long lastAccess = System.currentTimeMillis();

    public synchronized void append(String chunk) {
        content.append(chunk);
        touch();
    }

    public synchronized String text() {
        return content.toString();
    }

    public synchronized int length() {
        return content.length();
    }

    public void finish(String result) {
        this.state = State.DONE;
        this.resultJson = result;
        touch();
    }

    public void fail(String error) {
        this.state = State.FAILED;
        this.errorMsg = error;
        touch();
    }

    public void markStopped() {
        this.state = State.STOPPED;
        touch();
    }

    public State getState() {
        return state;
    }

    public String getResultJson() {
        return resultJson;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public boolean isCancelRequested() {
        return cancelRequested;
    }

    /** 请求停止：置标志并触发取消句柄（协作式取消，LC4j 流式停止消费增量） */
    public void requestCancel() {
        this.cancelRequested = true;
        Runnable h = this.cancelHandle;
        if (h != null) {
            h.run();
        }
    }

    public void bindCancel(Runnable cancelHandle) {
        this.cancelHandle = cancelHandle;
    }

    public void touch() {
        this.lastAccess = System.currentTimeMillis();
    }

    public long getLastAccess() {
        return lastAccess;
    }

    public long getCreateTime() {
        return createTime;
    }
}
