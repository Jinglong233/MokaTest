package com.mokatest.platform.demos.operationlog.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.mokatest.platform.demos.domain.ui.User;
import com.mokatest.platform.demos.mapper.UserMapper;
import com.mokatest.platform.demos.operationlog.annotation.OperationLog;
import com.mokatest.platform.demos.operationlog.config.OperationLogProperties;
import com.mokatest.platform.demos.operationlog.domain.SysOperationLog;
import com.mokatest.platform.demos.operationlog.enums.OperateType;
import com.mokatest.platform.demos.operationlog.service.SysOperationLogService;
import com.mokatest.platform.demos.operationlog.util.FieldCompareUtil;
import com.mokatest.platform.demos.operationlog.util.SpelUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 操作日志 AOP 拦截器
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogProperties properties;
    private final SysOperationLogService sysOperationLogService;
    private final UserMapper userMapper;
    private final ApplicationContext applicationContext;

    /**
     * 默认脱敏字段
     */
    private static final List<String> DEFAULT_SENSITIVE_FIELDS = Arrays.asList(
            "password", "passwd", "pwd", "token", "secret", "authorization", "accessToken", "refreshToken"
    );

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint point, OperationLog operationLog) throws Throwable {
        if (!properties.isEnabled()) {
            return point.proceed();
        }

        long startTime = System.currentTimeMillis();
        Object result = null;
        int responseCode = 200;
        String responseMsg = "success";

        // 【关键】UPDATE/DELETE 操作先缓存旧数据，避免 point.proceed() 后数据库已更新
        Object oldEntity = null;
        if (operationLog.type() == OperateType.UPDATE
                && operationLog.compareClass() != Void.class) {
            oldEntity = queryOldEntity(point, operationLog);
        }
        if (operationLog.type() == OperateType.DELETE) {
            oldEntity = queryOldEntityForDelete(point, operationLog);
        }

        try {
            result = point.proceed();
        } catch (Throwable e) {
            responseCode = 500;
            responseMsg = e.getMessage();
            throw e;
        } finally {
            try {
                long duration = System.currentTimeMillis() - startTime;
                SysOperationLog sysLog = buildLog(point, operationLog, result, responseCode, responseMsg, duration, oldEntity);
                saveLogAfterCommit(sysLog, responseCode);
            } catch (Exception ex) {
                log.error("操作日志记录失败", ex);
            }
        }

        return result;
    }

    /**
     * 成功日志延迟到事务提交后保存，避免主事务回滚产生"幽灵日志"；
     * 失败日志（异常）立即保存——此时事务必然回滚，不影响。
     */
    private void saveLogAfterCommit(SysOperationLog sysLog, int responseCode) {
        if (responseCode != 200) {
            sysOperationLogService.asyncSave(sysLog);
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sysOperationLogService.asyncSave(sysLog);
                }
            });
        } else {
            sysOperationLogService.asyncSave(sysLog);
        }
    }

    /**
     * 在 point.proceed() 之前查询旧数据
     */
    private Object queryOldEntity(ProceedingJoinPoint point, OperationLog operationLog) {
        try {
            Object[] args = point.getArgs();
            Class<?> compareClass = operationLog.compareClass();
            Object newEntity = null;
            for (Object arg : args) {
                if (arg != null && compareClass.isAssignableFrom(arg.getClass())) {
                    newEntity = arg;
                    break;
                }
            }
            if (newEntity == null) return null;

            Field idField = compareClass.getDeclaredField("id");
            idField.setAccessible(true);
            Object id = idField.get(newEntity);
            if (id == null) return null;

            return selectById((Serializable) id, compareClass);
        } catch (Exception e) {
            return null;
        }
    }

    private SysOperationLog buildLog(ProceedingJoinPoint point, OperationLog operationLog,
                                     Object result, int responseCode, String responseMsg, long duration,
                                     Object cachedOldEntity) {
        SysOperationLog sysLog = new SysOperationLog();
        sysLog.setModule(operationLog.module());
        sysLog.setOperateType(operationLog.type().name());
        sysLog.setTargetType(operationLog.targetType());
        sysLog.setResponseCode(responseCode);
        sysLog.setResponseMsg(responseMsg);
        sysLog.setDurationMs((int) duration);
        sysLog.setOperateTime(new Date());

        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        Object[] args = point.getArgs();

        String targetId = SpelUtil.parse(operationLog.targetId(), method, args, applicationContext);
        String targetName = SpelUtil.parse(operationLog.targetName(), method, args, applicationContext);
        String description = SpelUtil.parse(operationLog.description(), method, args, applicationContext);

        // SpEL 解析失败时的 fallback：从参数对象中反射获取 id 和名称
        if ((targetId == null || targetId.isEmpty()) && operationLog.compareClass() != Void.class) {
            Object entityArg = findEntityArg(args, operationLog.compareClass());
            if (entityArg != null) {
                targetId = getFieldValueAsString(entityArg, "id");
                if (targetName == null || targetName.isEmpty()) {
                    targetName = getFieldValueAsString(entityArg, "title");
                    if (targetName == null) targetName = getFieldValueAsString(entityArg, "name");
                    if (targetName == null) targetName = getFieldValueAsString(entityArg, "caseName");
                    if (targetName == null) targetName = getFieldValueAsString(entityArg, "planName");
                    if (targetName == null) targetName = getFieldValueAsString(entityArg, "moduleName");
                }
            }
        }

        if (targetId != null && !targetId.isEmpty()) {
            try {
                sysLog.setTargetId(Long.valueOf(targetId));
            } catch (NumberFormatException ignored) {
                sysLog.setTargetName(targetId);
            }
        }
        // targetName 为空时保留 targetId 解析失败时的兜底值（如批量删除的 ID 列表）
        if (targetName != null && !targetName.isEmpty()) {
            sysLog.setTargetName(targetName);
        }
        sysLog.setDescription(description);

        // 字段级变更对比（使用提前查询的旧数据）
        if (operationLog.type() == OperateType.UPDATE
                && operationLog.compareClass() != Void.class
                && cachedOldEntity != null) {
            String fieldChanges = compareFields(point, operationLog, cachedOldEntity);
            if (fieldChanges != null) {
                sysLog.setDescription(fieldChanges);
            }
        }

        // CREATE 操作：自动生成创建内容摘要
        if (operationLog.type() == OperateType.CREATE
                && (sysLog.getDescription() == null || sysLog.getDescription().isEmpty())) {
            String createSummary = buildCreateSummary(point, operationLog);
            if (createSummary != null) {
                sysLog.setDescription(createSummary);
            }
        }

        // DELETE 操作：自动生成删除描述
        if (operationLog.type() == OperateType.DELETE
                && (sysLog.getDescription() == null || sysLog.getDescription().isEmpty())) {
            String deleteDesc = buildDeleteDescription(sysLog, cachedOldEntity);
            if (deleteDesc != null) {
                sysLog.setDescription(deleteDesc);
            }
        }

        if (operationLog.recordParams()) {
            sysLog.setRequestParams(buildRequestParams(args, operationLog.sensitiveFields()));
        }

        try {
            if (StpUtil.isLogin()) {
                int loginId = StpUtil.getLoginIdAsInt();
                sysLog.setOperatorId(loginId);
                // 从 user 表查昵称，失败则 fallback 到 loginId
                String nickName = getOperatorNickName(loginId);
                sysLog.setOperatorName(nickName);
            }
        } catch (Exception ignored) {
        }

        // 如果 targetName 为空但有 targetId，尝试自动补充名称：
        // 优先用 compareClass，未配置时按 targetType 推断实体类（成员类对象按 user 解析）
        if ((sysLog.getTargetName() == null || sysLog.getTargetName().isEmpty())
                && sysLog.getTargetId() != null) {
            Class<?> nameResolveClass = operationLog.compareClass() != Void.class
                    ? operationLog.compareClass()
                    : inferEntityClass(operationLog.targetType());
            if (nameResolveClass != null) {
                String autoName = getTargetNameFromDb(sysLog.getTargetId(), nameResolveClass);
                if (autoName != null) {
                    sysLog.setTargetName(autoName);
                }
            }
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            sysLog.setIp(getClientIp(request));
            sysLog.setUserAgent(request.getHeader("User-Agent"));
        }

        return sysLog;
    }

    /**
     * 从方法参数中查找指定类型的实体对象
     */
    private Object findEntityArg(Object[] args, Class<?> compareClass) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg != null && compareClass.isAssignableFrom(arg.getClass())) {
                return arg;
            }
        }
        return null;
    }

    /**
     * 反射获取对象字段值并转为字符串
     */
    private String getFieldValueAsString(Object obj, String fieldName) {
        if (obj == null) return null;
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(obj);
            return value != null ? value.toString() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 获取操作人昵称
     */
    private String getOperatorNickName(int loginId) {
        try {
            User user = userMapper.selectById(loginId);
            if (user != null) {
                if (user.getNickname() != null && !user.getNickname().isEmpty()) {
                    return user.getNickname();
                }
                if (user.getUsername() != null && !user.getUsername().isEmpty()) {
                    return user.getUsername();
                }
            }
        } catch (Exception e) {
            log.warn("[操作日志] 获取操作人昵称失败", e);
        }
        return String.valueOf(loginId);
    }

    /**
     * 从数据库自动查询目标对象名称（用于 delete/transition 等操作补充 targetName）
     */
    private String getTargetNameFromDb(Long targetId, Class<?> entityClass) {
        try {
            Object entity = selectById((Serializable) targetId, entityClass);
            if (entity == null) {
                return null;
            }
            // 常见名称字段按优先级尝试
            List<String> nameFields = Arrays.asList(
                    "title", "name", "nickname", "username", "caseName", "planName", "moduleName", "setName",
                    "projectName", "teamName", "roleName", "content", "reqCode", "bugCode", "description"
            );
            for (String fieldName : nameFields) {
                try {
                    Field field = entityClass.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object value = field.get(entity);
                    if (value != null) {
                        String str = value.toString();
                        if (!str.isEmpty()) {
                            return str;
                        }
                    }
                } catch (NoSuchFieldException ignored) {
                    // 当前实体没有该字段，继续下一个
                }
            }
        } catch (Exception e) {
            log.warn("[操作日志] 自动查询目标名称失败", e);
        }
        return null;
    }

    /**
     * 通用查旧数据：优先用 Db 工具，失败时通过 Spring 容器动态找对应 Mapper
     */
    @SuppressWarnings("unchecked")
    private Object selectById(Serializable id, Class<?> entityClass) {
        if (id == null || entityClass == null) {
            return null;
        }

        // 1. 先尝试 Db 工具
        try {
            Object result = Db.getById(id, entityClass);
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
        }

        // 2. Db 失败时，通过 ApplicationContext 查找泛型匹配的 BaseMapper
        try {
            String simpleName = entityClass.getSimpleName();
            // 常见 Mapper 命名：BugMapper、RequirementMapper 等
            String[] candidateNames = {
                    simpleName.substring(0, 1).toLowerCase() + simpleName.substring(1) + "Mapper",
                    simpleName + "Mapper"
            };
            for (String beanName : candidateNames) {
                if (applicationContext.containsBean(beanName)) {
                    Object bean = applicationContext.getBean(beanName);
                    if (bean instanceof BaseMapper) {
                        Object result = ((BaseMapper<?>) bean).selectById(id);
                        return result;
                    }
                }
            }
            // 兜底：遍历所有 BaseMapper Bean，找泛型参数匹配的
            java.util.Map<String, BaseMapper> mapperMap = applicationContext.getBeansOfType(BaseMapper.class);
            for (BaseMapper<?> mapper : mapperMap.values()) {
                Class<?> mapperEntityClass = extractEntityClass(mapper);
                if (mapperEntityClass != null && mapperEntityClass.equals(entityClass)) {
                    Object result = mapper.selectById(id);
                    return result;
                }
            }
        } catch (Exception e) {
        }
        return null;
    }

    /**
     * 从 Mapper 实例中提取泛型实体类（支持 JDK 动态代理）
     */
    private Class<?> extractEntityClass(BaseMapper<?> mapper) {
        try {
            // 1. 从代理类直接实现的接口往上找
            Class<?> clazz = mapper.getClass();
            while (clazz != null && clazz != Object.class) {
                for (java.lang.reflect.Type type : clazz.getGenericInterfaces()) {
                    Class<?> found = extractEntityFromType(type);
                    if (found != null) return found;
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Class<?> extractEntityFromType(java.lang.reflect.Type type) {
        if (type instanceof java.lang.reflect.ParameterizedType) {
            java.lang.reflect.ParameterizedType pt = (java.lang.reflect.ParameterizedType) type;
            java.lang.reflect.Type rawType = pt.getRawType();
            if (rawType instanceof Class) {
                Class<?> rawClass = (Class<?>) rawType;
                if (rawClass == BaseMapper.class) {
                    java.lang.reflect.Type[] args = pt.getActualTypeArguments();
                    if (args.length > 0 && args[0] instanceof Class) {
                        return (Class<?>) args[0];
                    }
                }
                // 递归检查父接口，如 BugMapper extends BaseMapper<Bug>
                if (BaseMapper.class.isAssignableFrom(rawClass)) {
                    for (java.lang.reflect.Type parentType : rawClass.getGenericInterfaces()) {
                        Class<?> found = extractEntityFromType(parentType);
                        if (found != null) return found;
                    }
                }
            }
        } else if (type instanceof Class) {
            // 如 BugMapper（非 ParameterizedType），继续检查其父接口
            for (java.lang.reflect.Type parentType : ((Class<?>) type).getGenericInterfaces()) {
                Class<?> found = extractEntityFromType(parentType);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * 字段变更对比（使用提前查询的旧数据）
     */
    private String compareFields(ProceedingJoinPoint point, OperationLog operationLog, Object oldEntity) {
        try {
            Object[] args = point.getArgs();
            Class<?> compareClass = operationLog.compareClass();

            Object newEntity = null;
            for (Object arg : args) {
                if (arg != null && compareClass.isAssignableFrom(arg.getClass())) {
                    newEntity = arg;
                    break;
                }
            }
            if (newEntity == null) {
                return null;
            }

            if (oldEntity == null) {
                return null;
            }

            List<FieldCompareUtil.FieldChange> changes = FieldCompareUtil.compare(
                    oldEntity, newEntity, operationLog.ignoreFields());
            if (changes.isEmpty()) {
                return null;
            }

            String changesJson = FieldCompareUtil.toJson(changes);
            changesJson = enrichFieldChangesWithNames(changesJson);
            return changesJson;
        } catch (Exception e) {
            log.warn("[操作日志] 字段对比失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 自动从参数中推断实体对象（当 compareClass 未显式配置时）
     */
    private Object findEntityArgAuto(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg == null) continue;
            // 排除基本类型和 JDK 内置类型
            if (arg instanceof String || arg instanceof Number || arg instanceof Date
                    || arg instanceof Collection || arg instanceof Map
                    || arg instanceof HttpServletRequest || arg instanceof HttpServletResponse
                    || arg instanceof MultipartFile) {
                continue;
            }
            // 检查是否有 id 字段，有则认为是实体对象
            try {
                arg.getClass().getDeclaredField("id");
                return arg;
            } catch (NoSuchFieldException e) {
                // 没有 id 字段，继续找下一个
            }
        }
        return null;
    }

    /**
     * DELETE 操作查询旧数据（通过 targetType 推断实体类）
     */
    private Object queryOldEntityForDelete(ProceedingJoinPoint point, OperationLog operationLog) {
        try {
            // 1. 从参数中找到 ID
            Serializable id = null;
            for (Object arg : point.getArgs()) {
                if (arg instanceof Integer) {
                    id = (Integer) arg;
                    break;
                }
                if (arg instanceof Long) {
                    id = (Long) arg;
                    break;
                }
                if (arg instanceof List && !((List<?>) arg).isEmpty()) {
                    Object first = ((List<?>) arg).get(0);
                    if (first instanceof Integer) {
                        id = (Integer) first;
                        break;
                    }
                }
            }
            if (id == null) {
                return null;
            }

            // 2. 根据 targetType 推断实体类
            String targetType = operationLog.targetType();
            Class<?> entityClass = inferEntityClass(targetType);
            if (entityClass == null) {
                return null;
            }

            // 3. 查询数据库
            Object result = selectById(id, entityClass);
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 根据 targetType 推断实体类
     */
    private Class<?> inferEntityClass(String targetType) {
        try {
            switch (targetType) {
                case "bug": return Class.forName("com.mokatest.platform.demos.qa.domain.Bug");
                case "requirement": return Class.forName("com.mokatest.platform.demos.qa.domain.Requirement");
                case "testCase": return Class.forName("com.mokatest.platform.demos.qa.domain.TestCase");
                case "testCaseSet": return Class.forName("com.mokatest.platform.demos.qa.domain.TestCaseSet");
                case "qaModule": return Class.forName("com.mokatest.platform.demos.qa.domain.QaModule");
                case "testPlan": return Class.forName("com.mokatest.platform.demos.qa.domain.TestPlan");
                case "bugComment": return Class.forName("com.mokatest.platform.demos.qa.domain.BugComment");
                case "scene": return Class.forName("com.mokatest.platform.demos.automation.domain.Scene");
                case "plan": return Class.forName("com.mokatest.platform.demos.automation.domain.Plan");
                case "task": return Class.forName("com.mokatest.platform.demos.automation.domain.Task");
                case "user": return Class.forName("com.mokatest.platform.demos.domain.ui.User");
                case "project": return Class.forName("com.mokatest.platform.demos.domain.ui.Project");
                case "team": return Class.forName("com.mokatest.platform.demos.domain.ui.Team");
                case "role": return Class.forName("com.mokatest.platform.demos.domain.ui.Role");
                // 成员类对象的 targetId 是用户ID，按 user 表解析名称
                case "teamMember":
                case "projectMember": return Class.forName("com.mokatest.platform.demos.domain.ui.User");
                default: return null;
            }
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * 构建创建内容摘要（CREATE 操作）
     */
    private String buildCreateSummary(ProceedingJoinPoint point, OperationLog operationLog) {
        try {
            Object[] args = point.getArgs();
            Class<?> compareClass = operationLog.compareClass();
            Object newEntity = null;

            if (compareClass != Void.class) {
                // 显式配置了 compareClass
                for (Object arg : args) {
                    if (arg != null && compareClass.isAssignableFrom(arg.getClass())) {
                        newEntity = arg;
                        break;
                    }
                }
            } else {
                // 自动推断
                newEntity = findEntityArgAuto(args);
            }

            if (newEntity == null) {
                return null;
            }
            String summary = FieldCompareUtil.buildCreateSummary(newEntity, operationLog.ignoreFields());
            summary = enrichSummaryWithNames(summary);
            return summary;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 构建删除描述（DELETE 操作）
     */
    private String buildDeleteDescription(SysOperationLog sysLog, Object oldEntity) {
        if (oldEntity != null) {
            String summary = FieldCompareUtil.buildDeleteSummary(oldEntity);
            if (summary != null) {
                return enrichSummaryWithNames(summary);
            }
        }
        if (sysLog.getTargetName() != null && !sysLog.getTargetName().isEmpty()) {
            return "删除对象: " + sysLog.getTargetName();
        }
        if (sysLog.getTargetId() != null) {
            return "删除对象 ID: " + sysLog.getTargetId();
        }
        return null;
    }

    /**
     * 对字段变更 JSON 中的关联 ID 字段补充名称
     */
    private String enrichFieldChangesWithNames(String changesJson) {
        if (changesJson == null) return null;
        try {
            List<FieldCompareUtil.FieldChange> changes = com.alibaba.fastjson.JSON.parseArray(changesJson, FieldCompareUtil.FieldChange.class);
            for (FieldCompareUtil.FieldChange change : changes) {
                String oldDisplay = resolveRelatedName(change.getField(), change.getOld());
                if (oldDisplay != null) change.setOldDisplayValue(oldDisplay);
                String neDisplay = resolveRelatedName(change.getField(), change.getNe());
                if (neDisplay != null) change.setNeDisplayValue(neDisplay);
            }
            return com.alibaba.fastjson.JSON.toJSONString(changes);
        } catch (Exception e) {
            return changesJson;
        }
    }

    /**
     * 对摘要 JSON 中的关联 ID 字段补充名称（如 parentId → "需求标题 (ID:14)"）
     */
    private String enrichSummaryWithNames(String summaryJson) {
        if (summaryJson == null) return null;
        try {
            List<FieldCompareUtil.CreateField> fields = com.alibaba.fastjson.JSON.parseArray(summaryJson, FieldCompareUtil.CreateField.class);
            for (FieldCompareUtil.CreateField field : fields) {
                String displayValue = resolveRelatedName(field.getField(), field.getValue());
                if (displayValue != null) {
                    field.setDisplayValue(displayValue);
                }
            }
            return com.alibaba.fastjson.JSON.toJSONString(fields);
        } catch (Exception e) {
            return summaryJson;
        }
    }

    /**
     * 根据字段名和值，查询关联对象名称
     */
    private String resolveRelatedName(String fieldName, String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            int id = Integer.parseInt(value);
            switch (fieldName) {
                case "parentId":
                case "requirementId": {
                    Object bean = applicationContext.getBean("requirementMapper");
                    if (bean instanceof BaseMapper) {
                        Object obj = ((BaseMapper<?>) bean).selectById(id);
                        if (obj != null) {
                            String title = getFieldValueAsString(obj, "title");
                            return title + " (ID:" + value + ")";
                        }
                    }
                    break;
                }
                case "testCaseId": {
                    Object bean = applicationContext.getBean("testCaseMapper");
                    if (bean instanceof BaseMapper) {
                        Object obj = ((BaseMapper<?>) bean).selectById(id);
                        if (obj != null) {
                            String name = getFieldValueAsString(obj, "caseName");
                            return name + " (ID:" + value + ")";
                        }
                    }
                    break;
                }
                case "moduleId": {
                    Object bean = applicationContext.getBean("qaModuleMapper");
                    if (bean instanceof BaseMapper) {
                        Object obj = ((BaseMapper<?>) bean).selectById(id);
                        if (obj != null) {
                            String name = getFieldValueAsString(obj, "moduleName");
                            return name + " (ID:" + value + ")";
                        }
                    }
                    break;
                }
                case "teamId": {
                    Object bean = applicationContext.getBean("teamMapper");
                    if (bean instanceof BaseMapper) {
                        Object obj = ((BaseMapper<?>) bean).selectById(id);
                        if (obj != null) {
                            String name = getFieldValueAsString(obj, "teamName");
                            return name + " (ID:" + value + ")";
                        }
                    }
                    break;
                }
                case "assigneeId":
                case "reporterId":
                case "ownerId":
                case "createUserId":
                case "updateUserId": {
                    User user = userMapper.selectById(id);
                    if (user != null) {
                        String name = user.getNickname() != null && !user.getNickname().isEmpty()
                                ? user.getNickname() : user.getUsername();
                        return name + " (ID:" + value + ")";
                    }
                    break;
                }
            }
        } catch (Exception e) {
            // 忽略，保持原值
        }
        return null;
    }

    private String buildRequestParams(Object[] args, String sensitiveFields) {
        if (args == null || args.length == 0) {
            return null;
        }
        List<Object> validArgs = new ArrayList<>();
        for (Object arg : args) {
            if (arg == null) continue;
            if (arg instanceof HttpServletRequest || arg instanceof HttpServletResponse
                    || arg instanceof MultipartFile) continue;
            validArgs.add(arg);
        }
        if (validArgs.isEmpty()) return null;

        String paramsJson;
        try {
            paramsJson = JSON.toJSONString(validArgs.size() == 1 ? validArgs.get(0) : validArgs);
        } catch (Exception e) {
            return null;
        }
        // 先脱敏再截断：截断后的串不是合法 JSON，会导致脱敏被跳过
        try {
            Object parsed = JSON.parse(paramsJson);
            List<String> sensitiveList = new ArrayList<>();
            for (String f : DEFAULT_SENSITIVE_FIELDS) {
                sensitiveList.add(f.toLowerCase());
            }
            if (sensitiveFields != null && !sensitiveFields.isEmpty()) {
                for (String f : sensitiveFields.split(",")) {
                    sensitiveList.add(f.trim().toLowerCase());
                }
            }
            maskSensitiveValue(parsed, sensitiveList);
            paramsJson = JSON.toJSONString(parsed);
        } catch (Exception ignored) {
        }
        if (paramsJson.length() > 2000) {
            paramsJson = paramsJson.substring(0, 2000) + "...(truncated)";
        }
        return paramsJson;
    }

    private void maskSensitiveValue(Object node, List<String> sensitiveFields) {
        if (node instanceof JSONObject) {
            JSONObject jsonObject = (JSONObject) node;
            for (String key : jsonObject.keySet()) {
                if (sensitiveFields.contains(key.trim().toLowerCase())) {
                    jsonObject.put(key, "******");
                } else {
                    maskSensitiveValue(jsonObject.get(key), sensitiveFields);
                }
            }
        } else if (node instanceof com.alibaba.fastjson.JSONArray) {
            for (Object item : (com.alibaba.fastjson.JSONArray) node) {
                maskSensitiveValue(item, sensitiveFields);
            }
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
