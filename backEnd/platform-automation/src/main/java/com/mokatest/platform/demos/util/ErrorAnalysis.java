package com.mokatest.platform.demos.util;

public class ErrorAnalysis {
    private String errorType;        // 错误类型
    private int timeout;            // 超时时间
    private String operation;       // 操作类型
    private String rootCause;       // 根本原因
    private String retryPattern;    // 重试模式
    private String elementInfo;     // 元素信息
    private String suggestion;      // 解决建议

    // 构造函数
    public ErrorAnalysis() {}

    // Getter 和 Setter 方法
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    
    public int getTimeout() { return timeout; }
    public void setTimeout(int timeout) { this.timeout = timeout; }
    
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    
    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }
    
    public String getRetryPattern() { return retryPattern; }
    public void setRetryPattern(String retryPattern) { this.retryPattern = retryPattern; }
    
    public String getElementInfo() { return elementInfo; }
    public void setElementInfo(String elementInfo) { this.elementInfo = elementInfo; }
    
    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }

    @Override
    public String toString() {
        return String.format(
            "错误分析报告:\n" +
            "  错误类型: %s\n" +
            "  操作类型: %s\n" +
            "  超时时间: %dms\n" +
            "  根本原因: %s\n" +
            "  重试模式: %s\n" +
            "  元素信息: %s\n" +
            "  解决建议: %s",
            errorType, operation, timeout, rootCause, retryPattern, elementInfo, suggestion
        );
    }
}