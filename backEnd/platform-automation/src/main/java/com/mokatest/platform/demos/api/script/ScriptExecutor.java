package com.mokatest.platform.demos.api.script;

import com.mokatest.platform.demos.api.domain.requestModel.ScriptItem;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.ResourceLimits;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * JavaScript 脚本执行器（基于 GraalJS）
 *
 * 功能说明：
 *   - 执行前置/后置 JavaScript 脚本（支持单脚本和脚本列表）
 *   - 向脚本暴露 context 对象（变量读写、日志、断言、工具函数）
 *   - 脚本列表按 sort 排序，只执行 enabled=true 的脚本
 *   - 线程安全：每个执行创建独立的 Graal Context
 *
 * 使用示例：
 *   ScriptContext ctx = new ScriptContext();
 *   ctx.setVariables(envVars);
 *   ScriptResult result = ScriptExecutor.executePreScript(preScriptCode, ctx, requestInfo);
 */
@Slf4j
public class ScriptExecutor {

    /** GraalVM 引擎（可复用，线程安全） */
    private static final Engine ENGINE = Engine.newBuilder("js")
            .allowExperimentalOptions(true)
            .option("js.ecmascript-version", "2022")
            .option("js.nashorn-compat", "true")
            .option("engine.WarnInterpreterOnly", "false")
            .build();

    /** 单个脚本最大可执行语句数，防止死循环 hang 住请求 */
    private static final long SCRIPT_STATEMENT_LIMIT = 10_000_000L;

    /** 复用的资源限制配置 */
    private static final ResourceLimits SCRIPT_RESOURCE_LIMITS = ResourceLimits.newBuilder()
            .statementLimit(SCRIPT_STATEMENT_LIMIT, source -> true)
            .build();

    /**
     * 执行前置脚本列表（按 sort 排序，只执行 enabled=true 的脚本）
     *
     * @param scriptItems 前置脚本列表
     * @param variables   当前变量上下文
     * @param request     请求信息（脚本可修改）
     * @return 脚本执行结果（变量修改、日志、断言）
     */
    public static ScriptResult executePreScripts(List<ScriptItem> scriptItems,
                                                  Map<String, Object> variables,
                                                  ScriptRequest request) {
        return executePreScripts(scriptItems, variables, request, null);
    }

    /**
     * 执行前置脚本列表（带项目上下文，支持脚本内 fn.名称(...) 按名调用自定义函数）
     */
    public static ScriptResult executePreScripts(List<ScriptItem> scriptItems,
                                                  Map<String, Object> variables,
                                                  ScriptRequest request,
                                                  Integer projectId) {
        if (scriptItems == null || scriptItems.isEmpty()) {
            return ScriptResult.empty();
        }

        ScriptContext context = new ScriptContext();
        context.setVariables(variables);
        context.setRequest(request);
        context.setProjectId(projectId);

        return executeScriptList(scriptItems, context, "前置脚本");
    }

    /**
     * 执行后置脚本列表（按 sort 排序，只执行 enabled=true 的脚本）
     *
     * @param scriptItems 后置脚本列表
     * @param variables   当前变量上下文
     * @param response    响应信息（脚本只读）
     * @return 脚本执行结果（变量修改、日志、断言）
     */
    public static ScriptResult executePostScripts(List<ScriptItem> scriptItems,
                                                   Map<String, Object> variables,
                                                   ScriptResponse response) {
        return executePostScripts(scriptItems, variables, response, null);
    }

    /**
     * 执行后置脚本列表（带项目上下文，支持脚本内 fn.名称(...) 按名调用自定义函数）
     */
    public static ScriptResult executePostScripts(List<ScriptItem> scriptItems,
                                                   Map<String, Object> variables,
                                                   ScriptResponse response,
                                                   Integer projectId) {
        if (scriptItems == null || scriptItems.isEmpty()) {
            return ScriptResult.empty();
        }

        ScriptContext context = new ScriptContext();
        context.setVariables(variables);
        context.setResponse(response);
        context.setProjectId(projectId);

        return executeScriptList(scriptItems, context, "后置脚本");
    }

    /**
     * 执行脚本列表（内部方法）
     *
     * @param scriptItems 脚本列表
     * @param context     脚本上下文
     * @param scriptType  脚本类型（用于日志）
     * @return 合并后的执行结果
     */
    private static ScriptResult executeScriptList(List<ScriptItem> scriptItems,
                                                   ScriptContext context,
                                                   String scriptType) {
        ScriptResult mergedResult = new ScriptResult();
        mergedResult.setVariables(context.getVariables());
        mergedResult.setConsoleLogs(new ArrayList<>());
        mergedResult.setScriptAssertions(new ArrayList<>());
        mergedResult.setSuccess(true);

        // 过滤启用的脚本，按 sort 排序
        List<ScriptItem> enabledScripts = scriptItems.stream()
                .filter(item -> item != null && item.isEnabled())
                .filter(item -> item.getContent() != null && !item.getContent().trim().isEmpty())
                .sorted(Comparator.comparing(ScriptItem::getSort, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        if (enabledScripts.isEmpty()) {
            return ScriptResult.empty();
        }

        long totalStartTime = System.currentTimeMillis();
        int executedCount = 0;

        for (ScriptItem item : enabledScripts) {
            String scriptName = item.getName() != null ? item.getName() : ("脚本 #" + (executedCount + 1));
            mergedResult.getConsoleLogs().add("[" + scriptType + "] 开始执行: " + scriptName);

            ScriptResult singleResult = execute(item.getContent(), context);
            executedCount++;

            // 合并结果
            mergedResult.getConsoleLogs().addAll(singleResult.getConsoleLogs());
            mergedResult.getScriptAssertions().addAll(singleResult.getScriptAssertions());
            mergedResult.setVariables(singleResult.getVariables());

            if (!singleResult.isSuccess()) {
                mergedResult.setSuccess(false);
                mergedResult.setErrorMessage(
                        "脚本 '" + scriptName + "' 执行失败: " + singleResult.getErrorMessage()
                );
                mergedResult.getConsoleLogs().add(
                        "[ERROR] 脚本 '" + scriptName + "' 执行失败: " + singleResult.getErrorMessage()
                );
                break; // 一个脚本失败就停止后续脚本执行
            } else {
                mergedResult.getConsoleLogs().add(
                        "[" + scriptType + "] 完成: " + scriptName + " (" + singleResult.getExecutionTimeMs() + "ms)"
                );
            }
        }

        long totalCost = System.currentTimeMillis() - totalStartTime;
        mergedResult.setExecutionTimeMs(totalCost);
        mergedResult.getConsoleLogs().add("[" + scriptType + "] 共执行 " + executedCount + " 个脚本，总耗时 " + totalCost + "ms");

        log.debug("{} 执行完成，共 {} 个脚本，总耗时 {}ms，日志 {} 条",
                scriptType, executedCount, totalCost, mergedResult.getConsoleLogs().size());

        return mergedResult;
    }

    /**
     * 核心执行方法
     *
     * 关键设计：所有脚本共用同一个 ScriptContext（变量和请求/响应状态需要在脚本间传递），
     * 但每个脚本的执行结果只包含该脚本自身产生的日志和断言，避免累积污染。
     */
    private static ScriptResult execute(String scriptCode, ScriptContext context) {
        long startTime = System.currentTimeMillis();
        // 记录执行前的日志和断言数量，用于隔离本次执行的结果
        int logCountBefore = context.getConsoleLogs().size();
        int assertionCountBefore = context.getScriptAssertions().size();

        ScriptResult result = new ScriptResult();
        result.setVariables(context.getVariables());

        try (Context graalContext = Context.newBuilder("js")
                .engine(ENGINE)
                .allowHostAccess(HostAccess.ALL)
                // 禁止脚本通过 Java.type(...) 访问任意 Java 类，防止执行系统命令、读写文件等沙箱逃逸风险
                .allowHostClassLookup(className -> false)
                .allowIO(false)
                .resourceLimits(SCRIPT_RESOURCE_LIMITS)
                .build()) {

            // 将 Java 的 context 对象注入到 JS 中
            graalContext.getBindings("js").putMember("context", context);

            // 注入 console 对象（兼容标准 JS 的 console.log）
            String consolePolyfill = """
                var console = {
                    log: function() {
                        var msg = Array.prototype.slice.call(arguments).map(function(a) {
                            return typeof a === 'object' ? JSON.stringify(a) : String(a);
                        }).join(' ');
                        context.log(msg);
                    },
                    error: function() {
                        var msg = Array.prototype.slice.call(arguments).map(function(a) {
                            return typeof a === 'object' ? JSON.stringify(a) : String(a);
                        }).join(' ');
                        context.error(msg);
                    },
                    warn: function() {
                        var msg = Array.prototype.slice.call(arguments).map(function(a) {
                            return typeof a === 'object' ? JSON.stringify(a) : String(a);
                        }).join(' ');
                        context.log('[WARN] ' + msg);
                    },
                    info: function() {
                        var msg = Array.prototype.slice.call(arguments).map(function(a) {
                            return typeof a === 'object' ? JSON.stringify(a) : String(a);
                        }).join(' ');
                        context.log('[INFO] ' + msg);
                    }
                };
                """;
            graalContext.eval("js", consolePolyfill);

            // 注入 fn 代理：fn.函数名(...args) → 按「当前项目 + 名称」执行自定义函数
            // （与参数值中的 @fn(名称) 显示 / {{__CUSTOM(id)}} 存储是同一套函数）
            String fnPolyfill = """
                var fn = new Proxy({}, {
                    get: function(target, prop) {
                        return function() {
                            var args = Array.prototype.slice.call(arguments);
                            return context.callCustomByName(String(prop), JSON.stringify(args));
                        };
                    }
                });
                """;
            graalContext.eval("js", fnPolyfill);

            // 执行用户脚本
            graalContext.eval("js", scriptCode);

            // 收集结果 - 只取本次执行新增的日志和断言，复制到新列表避免引用污染
            List<String> allLogs = context.getConsoleLogs();
            result.setConsoleLogs(
                new ArrayList<>(allLogs.subList(logCountBefore, allLogs.size()))
            );
            List<ScriptContext.ScriptAssertion> allAssertions = context.getScriptAssertions();
            result.setScriptAssertions(
                new ArrayList<>(allAssertions.subList(assertionCountBefore, allAssertions.size()))
            );
            result.setVariables(context.getVariables());
            result.setSuccess(true);

            long cost = System.currentTimeMillis() - startTime;
            result.setExecutionTimeMs(cost);
            log.debug("脚本执行成功，耗时 {}ms，本次新增日志 {} 条", cost, result.getConsoleLogs().size());

        } catch (Exception e) {
            result.setSuccess(false);
            // 收集完整错误信息（包含异常类型和堆栈）
            StringBuilder errorMsg = new StringBuilder();
            errorMsg.append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());
            for (StackTraceElement element : e.getStackTrace()) {
                if (element.getClassName().contains("ScriptExecutor") ||
                    element.getClassName().contains("polyglot") ||
                    element.getClassName().contains("javascript")) {
                    errorMsg.append("\n  at ").append(element);
                }
            }
            String fullError = errorMsg.toString();
            result.setErrorMessage(fullError);
            // 错误时也只取本次新增的日志
            List<String> allLogs = context.getConsoleLogs();
            List<String> errorLogs = new ArrayList<>(allLogs.subList(logCountBefore, allLogs.size()));
            errorLogs.add("[ERROR] 脚本执行失败: " + fullError);
            result.setConsoleLogs(errorLogs);
            log.error("脚本执行失败: {}", fullError, e);
        }

        return result;
    }

    /**
     * 脚本执行结果
     */
    @lombok.Data
    public static class ScriptResult {
        private boolean success;
        private String errorMessage;
        private Map<String, Object> variables = new java.util.HashMap<>();
        private java.util.List<String> consoleLogs = new java.util.ArrayList<>();
        private java.util.List<ScriptContext.ScriptAssertion> scriptAssertions = new java.util.ArrayList<>();
        private long executionTimeMs;

        public static ScriptResult empty() {
            ScriptResult r = new ScriptResult();
            r.setSuccess(true);
            r.setVariables(java.util.Collections.emptyMap());
            r.setConsoleLogs(java.util.Collections.emptyList());
            r.setScriptAssertions(java.util.Collections.emptyList());
            return r;
        }
    }
}
