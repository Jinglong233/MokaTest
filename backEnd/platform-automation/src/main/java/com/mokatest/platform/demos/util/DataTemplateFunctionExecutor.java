package com.mokatest.platform.demos.util;

import com.alibaba.fastjson.JSON;
import com.mokatest.platform.demos.api.domain.DataTemplate;
import com.mokatest.platform.demos.api.domain.requestModel.MockFieldRule;
import com.mokatest.platform.demos.api.mapper.DataTemplateMapper;
import com.mokatest.platform.demos.config.saTokenConfig.ProjectContextHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据模板函数执行器
 *
 * 为 {@link FunctionParser} 的 {@code {{__TEMPLATE(id)__}}} 和
 * {@code {{__TEMPLATE_BATCH(id, count)__}}} 函数提供数据生成能力。</p>
 *
 * 由于 {@link FunctionParser} 是静态工具类，无法直接注入 Spring Bean，
 * 因此通过 {@link Component} + {@link PostConstruct} 将 Mapper 注入到静态引用中。</p>
 *
 * @author JingLong
 * @since 2026-06-17
 */
@Component
public class DataTemplateFunctionExecutor {

    private static final Logger log = LoggerFactory.getLogger(DataTemplateFunctionExecutor.class);

    @Resource
    private DataTemplateMapper dataTemplateMapper;

    private static DataTemplateMapper STATIC_MAPPER;

    @PostConstruct
    public void init() {
        STATIC_MAPPER = dataTemplateMapper;
    }

    /**
     * 生成单条数据并返回 JSON 字符串
     *
     * @param params 模板id
     * @return JSON 字符串
     */
    public static String generate(String params) {
        Integer id = parseId(params);
        if (id == null || STATIC_MAPPER == null) {
            log.warn("模板生成失败：id={}, STATIC_MAPPER={}", id, STATIC_MAPPER == null);
            return "{{__TEMPLATE(" + params + ")__}}";
        }
        DataTemplate template = loadTemplateChecked(id);
        if (template == null) {
            log.warn("模板生成失败：模板不存在/已删除/跨项目 id={}", id);
            return "{{__TEMPLATE(" + params + ")__}}";
        }
        if (template.getTemplateSchema() == null) {
            log.warn("模板生成失败：模板 id={} 的 templateSchema 为空", id);
            return "{}";
        }
        Map<String, Object> data = generateSingle(resolveSchema(template));
        return JSON.toJSONString(data);
    }

    /**
     * 批量生成数据并返回 JSON 字符串
     *
     * @param params 格式：id, count
     * @return JSON 数组字符串
     */
    public static String batchGenerate(String params) {
        if (params == null || params.trim().isEmpty()) {
            return "[]";
        }
        String[] args = params.split(",\\s*");
        Integer id = parseInt(args[0]);
        Integer count = args.length > 1 ? parseInt(args[1]) : 1;
        if (count == null || count <= 0) {
            count = 1;
        }
        if (count > 1000) {
            count = 1000;
        }
        if (id == null || STATIC_MAPPER == null) {
            return "{{__TEMPLATE_BATCH(" + params + ")__}}";
        }
        DataTemplate template = loadTemplateChecked(id);
        if (template == null) {
            log.warn("模板批量生成失败：模板不存在/已删除/跨项目 id={}", id);
            return "{{__TEMPLATE_BATCH(" + params + ")__}}";
        }
        List<Map<String, Object>> list = new ArrayList<>();
        MockFieldRule resolved = resolveSchema(template);
        for (int i = 0; i < count; i++) {
            list.add(generateSingle(resolved));
        }
        return JSON.toJSONString(list);
    }

    /**
     * 加载模板并做数据边界校验：
     * 1. 使用普通 selectById（@TableLogic 自动过滤已删除），已删除模板不再参与生成；
     * 2. 当前线程存在项目上下文（HTTP 请求线程由拦截器设置，异步执行线程由
     *    AbstractRequestExecutor 按 api_request.projectId 设置）时，强制模板归属该项目，
     *    防止跨项目引用（IDOR）。上下文为空时不拦截（如本地脚本工具直调）。
     */
    private static DataTemplate loadTemplateChecked(Integer id) {
        DataTemplate template = STATIC_MAPPER.selectById(id);
        if (template == null) {
            return null;
        }
        Integer ctxProjectId = ProjectContextHolder.getProjectId();
        if (ctxProjectId != null && !ctxProjectId.equals(template.getProjectId())) {
            log.warn("模板生成被拒：模板 id={} 属于项目 {}，当前执行上下文项目 {}", id, template.getProjectId(), ctxProjectId);
            return null;
        }
        return template;
    }

    private static Integer parseId(String params) {
        if (params == null || params.trim().isEmpty()) {
            return null;
        }
        String[] args = params.split(",\\s*");
        return parseInt(args[0]);
    }

    private static Integer parseInt(String str) {
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 按模板 ID 加载并解析继承链后的最终 schema（含项目边界校验），供结构校验使用。
     */
    public static MockFieldRule resolveSchemaById(Integer id) {
        if (id == null || STATIC_MAPPER == null) {
            return null;
        }
        DataTemplate template = loadTemplateChecked(id);
        return template == null ? null : resolveSchema(template);
    }

    /**
     * 解析模板最终 schema：沿 extendsId 继承链逐层合并（父树为基础，子树同名字段覆盖、
     * 新字段追加），visited set + 深度上限防循环继承。
     */
    public static MockFieldRule resolveSchema(DataTemplate template) {
        MockFieldRule schema = template.getTemplateSchema();
        Integer extendsId = template.getExtendsId();
        java.util.Set<Integer> visited = new java.util.HashSet<>();
        visited.add(template.getId());
        int depth = 0;
        while (extendsId != null && depth < MAX_INHERIT_DEPTH) {
            if (!visited.add(extendsId)) {
                log.warn("模板继承检测到循环：templateId={}, extendsId={}", template.getId(), extendsId);
                break;
            }
            DataTemplate parent = STATIC_MAPPER.selectById(extendsId);
            if (parent == null || parent.getTemplateSchema() == null
                    || !parent.getProjectId().equals(template.getProjectId())) {
                break;
            }
            schema = mergeSchema(parent.getTemplateSchema(), schema);
            extendsId = parent.getExtendsId();
            depth++;
        }
        return schema;
    }

    /** 继承链最大深度 */
    private static final int MAX_INHERIT_DEPTH = 5;

    /**
     * 合并 schema（浅合并一层 children）：父树为基础，子树同名 fieldName 覆盖、否则追加。
     * 返回新对象，不修改入参。
     */
    public static MockFieldRule mergeSchema(MockFieldRule parent, MockFieldRule child) {
        if (child == null) {
            return parent;
        }
        if (parent == null || parent.getChildren() == null || parent.getChildren().isEmpty()) {
            return child;
        }
        if (child.getChildren() == null || child.getChildren().isEmpty()) {
            return parent;
        }
        MockFieldRule merged = JSON.parseObject(JSON.toJSONString(parent), MockFieldRule.class);
        Map<String, MockFieldRule> byName = new java.util.LinkedHashMap<>();
        for (MockFieldRule f : merged.getChildren()) {
            if (f.getFieldName() != null) {
                byName.put(f.getFieldName(), f);
            }
        }
        for (MockFieldRule f : child.getChildren()) {
            if (f.getFieldName() != null) {
                byName.put(f.getFieldName(), f); // 同名覆盖，新字段追加
            }
        }
        merged.setChildren(new ArrayList<>(byName.values()));
        return merged;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> generateSingle(MockFieldRule root) {
        if (root == null) {
            return new java.util.LinkedHashMap<>();
        }
        Object result = MockRuleGenerator.generateValue(root);
        if (result instanceof Map) {
            return (Map<String, Object>) result;
        }
        Map<String, Object> wrap = new java.util.LinkedHashMap<>();
        wrap.put("value", result);
        return wrap;
    }
}
