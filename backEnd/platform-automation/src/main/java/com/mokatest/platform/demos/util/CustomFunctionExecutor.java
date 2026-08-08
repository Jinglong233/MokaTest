package com.mokatest.platform.demos.util;

import com.alibaba.fastjson.JSON;
import com.mokatest.platform.demos.api.domain.CustomFunction;
import com.mokatest.platform.demos.api.domain.requestModel.ScriptItem;
import com.mokatest.platform.demos.api.mapper.CustomFunctionMapper;
import com.mokatest.platform.demos.api.script.ScriptExecutor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义公共函数执行器
 *
 * 为 {@link FunctionParser} 的 {@code {{__CUSTOM(id, args...)__}}}、{@code @custom(id, args...)}
 * 以及脚本中的 {@code context.utils.custom(id, ...)} 提供用户自定义 JS 函数调用能力。
 *
 * 由于 {@link FunctionParser} 是静态工具类，无法直接注入 Spring Bean，
 * 因此通过 {@link Component} + {@link PostConstruct} 将 Mapper 注入到静态引用中。
 *
 * 执行方式：把用户函数体包裹成 {@code function(参数...) { 函数体 }}，连同示例参数一起
 * 丢进 GraalJS 沙箱（{@link ScriptExecutor}）执行，返回字符串结果。
 *
 * @author JingLong
 * @since 2026-07-31
 */
@Component
public class CustomFunctionExecutor {

    private static final Logger log = LoggerFactory.getLogger(CustomFunctionExecutor.class);

    @Resource
    private CustomFunctionMapper customFunctionMapper;

    private static CustomFunctionMapper STATIC_MAPPER;

    @PostConstruct
    public void init() {
        STATIC_MAPPER = customFunctionMapper;
    }

    /**
     * 供 FunctionParser 调用：解析 "id, arg1, arg2" 参数串，执行函数并返回字符串结果。
     * 执行失败时原样返回表达式，便于排查（与 TEMPLATE 函数行为一致）。
     *
     * @param params 格式：id, arg1, arg2（字符串参数可带单/双引号）
     * @return 函数执行结果的字符串形式
     */
    public static String generate(String params) {
        if (params == null || params.trim().isEmpty()) {
            return "{{__CUSTOM()__}}";
        }
        List<String> tokens = splitArgs(params);
        Integer id = parseInt(tokens.get(0));
        if (id == null || STATIC_MAPPER == null) {
            log.warn("自定义函数执行失败：id={}, STATIC_MAPPER={}", id, STATIC_MAPPER == null);
            return "{{__CUSTOM(" + params + ")__}}";
        }
        CustomFunction function = STATIC_MAPPER.selectByIdIncludeDeleted(id);
        if (function == null || (function.getIsDeleted() != null && function.getIsDeleted() == 1)) {
            log.warn("自定义函数执行失败：未找到函数 id={}", id);
            return "{{__CUSTOM(" + params + ")__}}";
        }
        List<Object> args = new ArrayList<>();
        for (int i = 1; i < tokens.size(); i++) {
            args.add(stripQuotes(tokens.get(i)));
        }
        RunResult result = execute(function.getFuncParams(), function.getFuncCode(), args, function.getProjectId());
        if (!result.isSuccess()) {
            log.warn("自定义函数执行失败：id={}, name={}, error={}", id, function.getFuncName(), result.getErrorMessage());
            return "{{__CUSTOM(" + params + ")__}}";
        }
        return result.getValue();
    }

    /**
     * 供脚本 / testRun 调用：按 id 加载函数并执行。
     */
    public static RunResult executeById(Integer id, List<Object> args) {
        if (id == null || STATIC_MAPPER == null) {
            return RunResult.fail("函数 id 为空或执行器未初始化");
        }
        CustomFunction function = STATIC_MAPPER.selectByIdIncludeDeleted(id);
        if (function == null || (function.getIsDeleted() != null && function.getIsDeleted() == 1)) {
            return RunResult.fail("未找到自定义函数 id=" + id);
        }
        return execute(function.getFuncParams(), function.getFuncCode(), args, function.getProjectId());
    }

    /**
     * 供脚本内 fn.名称(...) 调用：按「项目 + 函数名」加载函数并执行。
     * 函数名在项目内唯一（保存时已强制约束），解析无歧义。
     */
    public static RunResult executeByName(Integer projectId, String funcName, List<Object> args) {
        if (projectId == null || funcName == null || funcName.trim().isEmpty() || STATIC_MAPPER == null) {
            return RunResult.fail("项目/函数名为空或执行器未初始化");
        }
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CustomFunction> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        wrapper.eq("project_id", projectId).eq("func_name", funcName.trim());
        CustomFunction function = STATIC_MAPPER.selectOne(wrapper);
        if (function == null) {
            return RunResult.fail("当前项目未找到自定义函数: " + funcName);
        }
        return execute(function.getFuncParams(), function.getFuncCode(), args, function.getProjectId());
    }

    /**
     * 核心执行：把函数体包裹后丢进 GraalJS 沙箱执行。
     *
     * @param funcParams 参数名定义（逗号分隔，如 "text,key"），可为空
     * @param funcCode   JS 函数体（return 出结果）
     * @param args       实际参数（按 funcParams 顺序）
     * @param projectId  项目 id（函数体内再调 fn.其他函数(...) 时解析用，可为 null）
     */
    public static RunResult execute(String funcParams, String funcCode, List<Object> args) {
        return execute(funcParams, funcCode, args, null);
    }

    public static RunResult execute(String funcParams, String funcCode, List<Object> args, Integer projectId) {
        if (funcCode == null || funcCode.trim().isEmpty()) {
            return RunResult.fail("函数体不能为空");
        }
        String params = funcParams == null ? "" : funcParams.trim();
        String argsJson = JSON.toJSONString(args != null ? args : List.of());

        // 包裹脚本：定义函数 → 调用 → 结果写回变量（对象 JSON 序列化，标量保持原样）
        String wrapper = "var __customArgs = " + argsJson + ";\n"
                + "var __customFn = function(" + params + ") {\n" + funcCode + "\n};\n"
                + "var __customReturn = __customFn.apply(null, __customArgs);\n"
                + "context.setVariable('__customResult', "
                + "  __customReturn == null ? '' : "
                + "  (typeof __customReturn === 'object' ? JSON.stringify(__customReturn) : String(__customReturn)));\n";

        ScriptItem item = new ScriptItem();
        item.setName("自定义函数");
        item.setContent(wrapper);
        item.setEnabled(true);
        item.setSort(0);

        Map<String, Object> variables = new HashMap<>();
        long start = System.currentTimeMillis();
        ScriptExecutor.ScriptResult scriptResult = ScriptExecutor.executePreScripts(List.of(item), variables, null, projectId);
        long cost = System.currentTimeMillis() - start;

        if (!scriptResult.isSuccess()) {
            return RunResult.fail(scriptResult.getErrorMessage(), scriptResult.getConsoleLogs(), cost);
        }
        Object value = scriptResult.getVariables().get("__customResult");
        return RunResult.ok(value != null ? String.valueOf(value) : "", scriptResult.getConsoleLogs(), cost);
    }

    /**
     * 按顶层逗号切分参数（尊重单/双引号内的逗号）
     */
    private static List<String> splitArgs(String params) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        Character quote = null;
        for (char c : params.toCharArray()) {
            if (quote == null && (c == '\'' || c == '"')) {
                quote = c;
                current.append(c);
            } else if (quote != null && c == quote) {
                quote = null;
                current.append(c);
            } else if (quote == null && c == ',') {
                tokens.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString().trim());
        }
        return tokens;
    }

    private static String stripQuotes(String token) {
        if (token == null || token.length() < 2) {
            return token;
        }
        char first = token.charAt(0);
        char last = token.charAt(token.length() - 1);
        if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
            return token.substring(1, token.length() - 1);
        }
        return token;
    }

    private static Integer parseInt(String str) {
        try {
            return Integer.parseInt(str.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 函数执行结果
     */
    @lombok.Data
    public static class RunResult {
        private boolean success;
        private String value;
        private String errorMessage;
        private List<String> consoleLogs = new ArrayList<>();
        private long executionTimeMs;

        public static RunResult ok(String value, List<String> logs, long cost) {
            RunResult r = new RunResult();
            r.setSuccess(true);
            r.setValue(value);
            r.setConsoleLogs(logs != null ? logs : new ArrayList<>());
            r.setExecutionTimeMs(cost);
            return r;
        }

        public static RunResult fail(String error) {
            return fail(error, new ArrayList<>(), 0);
        }

        public static RunResult fail(String error, List<String> logs, long cost) {
            RunResult r = new RunResult();
            r.setSuccess(false);
            r.setErrorMessage(error);
            r.setConsoleLogs(logs != null ? logs : new ArrayList<>());
            r.setExecutionTimeMs(cost);
            return r;
        }
    }
}
