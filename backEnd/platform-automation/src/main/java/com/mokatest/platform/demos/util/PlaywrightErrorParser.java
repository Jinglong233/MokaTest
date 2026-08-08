package com.mokatest.platform.demos.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaywrightErrorParser {

    /**
     * 解析完整的Playwright错误字符串
     */
    public static ErrorAnalysis parseFullError(String fullError) {
        // 预处理：分离错误信息和调用日志
        ErrorParts parts = separateErrorParts(fullError);

        ErrorAnalysis analysis = new ErrorAnalysis();

        // 解析错误类型和超时时间
        parseErrorTypeAndTimeout(parts.errorMessage, analysis);

        // 解析操作类型
        analysis.setOperation(extractOperation(parts.callLog) + "\n");

        // 解析根本原因
        analysis.setRootCause(extractRootCause(parts.callLog) + "\n");

        // 解析重试模式
        analysis.setRetryPattern(extractRetryPattern(parts.callLog) + "\n");

        // 解析元素信息
        analysis.setElementInfo(extractElementInfo(parts.callLog) + "\n");

        // 生成解决建议
        analysis.setSuggestion(generateSuggestion(analysis) + "\n");

        return analysis;
    }

    /**
     * 分离错误信息的各个部分
     */
    private static ErrorParts separateErrorParts(String fullError) {
        ErrorParts parts = new ErrorParts();

        // 分离错误消息和调用日志
        String[] mainParts = fullError.split("Call log:");
        if (mainParts.length > 0) {
            parts.errorMessage = mainParts[0].trim();
        }
        if (mainParts.length > 1) {
            parts.callLog = mainParts[1].trim();
        }

        // 如果没有明确的Call log分隔，尝试其他方式
        if (parts.callLog == null || parts.callLog.isEmpty()) {
            if (fullError.contains("Call log:")) {
                parts.callLog = fullError.substring(fullError.indexOf("Call log:") + "Call log:".length()).trim();
            } else if (fullError.contains("waiting for")) {
                // 如果包含等待信息，可能是调用日志
                parts.callLog = extractCallLogFromMessage(fullError);
            }
        }

        return parts;
    }

    /**
     * 从错误消息中提取调用日志
     */
    private static String extractCallLogFromMessage(String errorMessage) {
        // 查找典型的调用日志模式
        Pattern callLogPattern = Pattern.compile("(waiting for [^-]+ - .+)", Pattern.DOTALL);
        Matcher matcher = callLogPattern.matcher(errorMessage);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    /**
     * 解析错误类型和超时时间
     */
    private static void parseErrorTypeAndTimeout(String errorMessage, ErrorAnalysis analysis) {
        if (errorMessage.contains("Timeout")) {
            analysis.setErrorType("TIMEOUT_ERROR");
            // 提取超时时间
            Pattern pattern = Pattern.compile("Timeout (\\d+)ms");
            Matcher matcher = pattern.matcher(errorMessage);
            if (matcher.find()) {
                analysis.setTimeout(Integer.parseInt(matcher.group(1)));
            }
        } else if (errorMessage.contains("Element not found") || errorMessage.contains("element not found")) {
            analysis.setErrorType("ELEMENT_NOT_FOUND");
        } else if (errorMessage.contains("not visible")) {
            analysis.setErrorType("ELEMENT_NOT_VISIBLE");
        } else if (errorMessage.contains("not enabled")) {
            analysis.setErrorType("ELEMENT_NOT_ENABLED");
        } else {
            analysis.setErrorType("UNKNOWN_ERROR");
        }
    }

    private static String extractOperation(String callLog) {
        if (callLog.contains("fill(")) return "FILL";
        if (callLog.contains("click(")) return "CLICK";
        if (callLog.contains("getByText(")) return "GET_BY_TEXT";
        if (callLog.contains("getByRole(")) return "GET_BY_ROLE";
        if (callLog.contains("locator(")) return "LOCATOR";
        return "UNKNOWN_OPERATION";
    }

    private static String extractRootCause(String callLog) {
        if (callLog.contains("element is not visible")) {
            return "元素不可见";
        } else if (callLog.contains("element not found")) {
            return "元素未找到";
        } else if (callLog.contains("element is not enabled")) {
            return "元素未启用";
        } else if (callLog.contains("element is detached")) {
            return "元素已分离";
        } else if (callLog.contains("Timeout")) {
            return "操作超时";
        }
        return "未知原因";
    }

    private static String extractRetryPattern(String callLog) {
        StringBuilder pattern = new StringBuilder();

        // 匹配重试次数和等待时间
        Pattern retryPattern = Pattern.compile("(\\d+) × waiting for|waiting (\\d+)ms");
        Matcher matcher = retryPattern.matcher(callLog);

        int retryCount = 0;
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                retryCount += Integer.parseInt(matcher.group(1));
            }
        }

        // 匹配等待时间序列
        Pattern waitPattern = Pattern.compile("waiting (\\d+)ms");
        Matcher waitMatcher = waitPattern.matcher(callLog);
        StringBuilder waitTimes = new StringBuilder();
        while (waitMatcher.find()) {
            if (waitTimes.length() > 0) waitTimes.append(" → ");
            waitTimes.append(waitMatcher.group(1)).append("ms\n");
        }

        if (retryCount > 0) {
            pattern.append("重试").append(retryCount).append("次\n");
            if (waitTimes.length() > 0) {
                pattern.append("，等待序列: ").append(waitTimes);
            }
        } else {
            pattern.append("无明确重试模式\n");
        }

        return pattern.toString();
    }

    private static String extractElementInfo(String callLog) {
        // 尝试提取元素信息
        Pattern elementPattern = Pattern.compile("resolved to <([^>]+)>");
        Matcher matcher = elementPattern.matcher(callLog);
        if (matcher.find()) {
            return matcher.group(0).split("resolved to")[1];
        }

        // 尝试提取定位器信息
        Pattern locatorPattern = Pattern.compile("waiting for (getByText\\([^)]+\\)|getByRole\\([^)]+\\)|locator\\" +
                "([^)]+\\))");
        Matcher locatorMatcher = locatorPattern.matcher(callLog);
        if (locatorMatcher.find()) {
            return locatorMatcher.group(1);
        }

        return "未找到元素信息\n";
    }

    private static String generateSuggestion(ErrorAnalysis analysis) {
        StringBuilder suggestion = new StringBuilder();

        switch (analysis.getRootCause()) {
            case "元素不可见":
                suggestion.append("1. 使用 page.waitForSelector(selector, { state: 'visible' })\n");
                suggestion.append("2. 检查元素是否被CSS隐藏 (display: none, visibility: hidden)\n");
                suggestion.append("3. 使用 locator.waitFor({ state: 'visible' }) 等待元素可见\n");
                suggestion.append("4. 增加超时时间: page.setDefaultTimeout(10000)\n");
                break;
            case "元素未找到":
                suggestion.append("1. 验证定位器是否正确\n");
                suggestion.append("2. 检查页面是否完全加载: page.waitForLoadState()\n");
                suggestion.append("3. 使用更稳定的定位策略\n");
                suggestion.append("4. 检查iframe或shadow DOM\n");
                break;
            case "操作超时":
                suggestion.append("1. 增加超时时间: page.setDefaultTimeout(15000)\n");
                suggestion.append("2. 检查网络连接和页面响应速度\n");
                suggestion.append("3. 添加明确的等待条件\n");
                suggestion.append("4. 使用 locator.waitFor() 确保元素就绪\n");
                break;
            default:
                suggestion.append("1. 检查元素状态和页面交互条件\n");
                suggestion.append("2. 添加适当的等待策略\n");
                suggestion.append("3. 验证定位器稳定性\n");
        }

        return suggestion.toString();
    }

    /**
     * 内部类：存储分离的错误部分
     */
    private static class ErrorParts {
        String errorMessage;
        String callLog;

        ErrorParts() {
            this.errorMessage = "";
            this.callLog = "";
        }
    }
}