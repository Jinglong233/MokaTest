package com.mokatest.platform.demos.element;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.domain.ui.Element;
import com.mokatest.platform.demos.domain.ui.LocatorCandidate;
import com.mokatest.platform.demos.domain.ui.Scene;
import com.mokatest.platform.demos.domain.ui.uiEnum.element.ElementType;
import com.mokatest.platform.demos.mapper.ElementMapper;
import com.mokatest.platform.demos.mapper.SceneMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 导入场景 / 录制保存时的「同步元素至元素库」联动服务。
 * 步骤内元素按「同名 + 同主定位」在目标目录下查重：存在则复用其 id 重映射步骤引用，
 * 不存在则新建元素（带完整定位链，只有单定位时合成单元素链）。
 */
@Slf4j
@Component
public class ElementLibrarySyncService {

    /**
     * stepDetail 中承载 ElementDTO 结构的 key
     */
    private static final List<String> ELEMENT_KEYS = Arrays.asList("element", "dragElement", "targetElement");

    @Resource
    private ElementMapper elementMapper;

    @Resource
    private SceneMapper sceneMapper;

    /**
     * 解析元素库目标目录：显式指定的 elementParentId 优先（<=0 的值含 -1 缺省哨兵，统一按未指定处理）；
     * 缺省取「与场景所属目录同名」的元素库顶层目录，不存在则自动创建；场景在根目录时落到元素库根目录
     */
    public Integer resolveTargetFolderId(String projectId, Integer elementParentId, Integer sceneParentId) {
        if (elementParentId != null && elementParentId > 0) {
            Element folder = elementMapper.selectById(elementParentId);
            if (folder != null && ElementType.FOLDER.name().equals(String.valueOf(folder.getElementType()))
                    && Objects.equals(projectId, folder.getProjectId())) {
                return elementParentId;
            }
            log.warn("[ElementSync] 指定的元素目标目录 id={} 不存在/非目录/不属于当前项目，降级为与场景同目录",
                    elementParentId);
        }
        if (sceneParentId == null || sceneParentId == 0) {
            return 0;
        }
        Scene sceneFolder = sceneMapper.selectById(sceneParentId);
        if (sceneFolder == null || !StringUtils.hasText(sceneFolder.getName())) {
            return 0;
        }
        String folderName = sceneFolder.getName();
        QueryWrapper<Element> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("project_id", projectId);
        queryWrapper.eq("element_type", ElementType.FOLDER);
        queryWrapper.eq("element_name", folderName);
        queryWrapper.eq("parent_id", 0);
        queryWrapper.orderByAsc("sort").orderByAsc("id");
        queryWrapper.last("LIMIT 1");
        List<Element> existed = elementMapper.selectList(queryWrapper);
        if (!existed.isEmpty()) {
            return existed.get(0).getId();
        }
        // 自动创建同名元素库目录
        Element folder = new Element();
        folder.setParentId(0);
        folder.setElementName(folderName);
        folder.setElementType(ElementType.FOLDER.name());
        folder.setProjectId(projectId);
        QueryWrapper<Element> countWrapper = new QueryWrapper<>();
        countWrapper.eq("project_id", projectId);
        countWrapper.eq("element_type", ElementType.FOLDER);
        countWrapper.eq("parent_id", 0);
        folder.setSort(elementMapper.selectCount(countWrapper).intValue() + 1);
        String userId = StpUtil.getLoginIdAsString();
        folder.setCreateUserId(userId);
        folder.setUpdateUserId(userId);
        elementMapper.insert(folder);
        log.info("[ElementSync] 自动创建元素库目录: projectId={}, name={}", projectId, folderName);
        return folder.getId();
    }

    /**
     * 同步单个步骤详情中的元素引用到元素库，并把 locator 重映射为真实库元素 id
     */
    public void syncStepDetailElements(Map<String, Object> detail, String projectId, Integer folderId) {
        syncStepDetailElements(detail, projectId, folderId, null);
    }

    /**
     * @param fallbackName 快照无 elementName 时的命名兜底（CRX/playwright 快照只有 type/value，可取步骤名）
     */
    public void syncStepDetailElements(Map<String, Object> detail, String projectId, Integer folderId,
                                       String fallbackName) {
        if (detail == null || projectId == null || folderId == null) {
            return;
        }
        for (String key : ELEMENT_KEYS) {
            Object elementObj = detail.get(key);
            if (elementObj instanceof Map) {
                syncElementReference((Map<String, Object>) elementObj, projectId, folderId, fallbackName);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void syncElementReference(Map<String, Object> elementMap, String projectId, Integer folderId,
                                      String fallbackName) {
        Map<String, Object> snapshot = pickEffectiveSnapshot(elementMap);
        if (snapshot == null) {
            return;
        }
        List<LocatorCandidate> chain = readChain(snapshot.get("locatorChain"));
        if (chain.isEmpty()) {
            String type = toText(snapshot.get("locatorType"));
            String value = toText(snapshot.get("locatorValue"));
            if (!StringUtils.hasText(type) || !StringUtils.hasText(value)) {
                return;
            }
            // 存量快照兼容：无链 TEXT 为旧「文本包含」语义
            if ("TEXT".equalsIgnoreCase(type)) {
                type = "TEXT_CONTAINS";
            }
            LocatorCandidate candidate = new LocatorCandidate();
            candidate.setType(type);
            candidate.setValue(value);
            chain.add(candidate);
        }
        LocatorCandidate primary = chain.get(0);

        String name = toText(snapshot.get("elementName"));
        if (!StringUtils.hasText(name) && StringUtils.hasText(fallbackName)) {
            name = fallbackName.trim();
        }
        if (!StringUtils.hasText(name)) {
            // 无名快照兜底：取主定位值截断
            String value = primary.getValue();
            name = value.length() > 30 ? value.substring(0, 30) : value;
        }
        Element lib = findDuplicate(projectId, folderId, name, primary.getType(), primary.getValue());
        if (lib == null) {
            lib = createElement(projectId, folderId, name, toText(snapshot.get("description")), chain);
        }

        // 重映射步骤的 locator 引用为真实库元素
        Map<String, Object> locator = new LinkedHashMap<>();
        locator.put("id", lib.getId());
        locator.put("elementName", lib.getElementName());
        locator.put("locatorType", lib.getLocatorType());
        locator.put("locatorValue", lib.getLocatorValue());
        // 命中已有库元素时写库的真实定位链（而非导入快照链），避免「库主定位 + 快照链」矛盾
        List<LocatorCandidate> libChain = ElementProcessor.resolveChain(lib);
        locator.put("locatorChain", libChain.isEmpty() ? chain : libChain);
        locator.put("projectId", projectId);
        elementMap.put("locator", locator);
        elementMap.put("locatorSource", "LIBRARY");
        if (!(elementMap.get("customLocator") instanceof Map)) {
            elementMap.put("customLocator", new LinkedHashMap<>());
        }
    }

    /**
     * 取步骤内生效的元素快照：locatorSource=CUSTOM 取自定义，LIBRARY 取库选；为空按「库选优先」
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> pickEffectiveSnapshot(Map<String, Object> elementMap) {
        Map<String, Object> locator = elementMap.get("locator") instanceof Map
                ? (Map<String, Object>) elementMap.get("locator") : null;
        Map<String, Object> custom = elementMap.get("customLocator") instanceof Map
                ? (Map<String, Object>) elementMap.get("customLocator") : null;
        boolean locatorHas = hasLocator(locator);
        boolean customHas = hasLocator(custom);
        String source = toText(elementMap.get("locatorSource"));
        if ("CUSTOM".equalsIgnoreCase(source)) {
            return customHas ? custom : (locatorHas ? locator : null);
        }
        // LIBRARY 或历史数据：库选优先
        return locatorHas ? locator : (customHas ? custom : null);
    }

    private boolean hasLocator(Map<String, Object> map) {
        return map != null && StringUtils.hasText(toText(map.get("locatorType")))
                && StringUtils.hasText(toText(map.get("locatorValue")));
    }

    /**
     * 读取快照中的定位链（兼容 Gson 反序列化出的 List<Map> 结构）
     */
    private List<LocatorCandidate> readChain(Object chainObj) {
        List<LocatorCandidate> chain = new ArrayList<>();
        if (!(chainObj instanceof List)) {
            return chain;
        }
        for (Object item : (List<Object>) chainObj) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<String, Object> itemMap = (Map<String, Object>) item;
            String type = toText(itemMap.get("type"));
            String value = toText(itemMap.get("value"));
            if (!StringUtils.hasText(type) || !StringUtils.hasText(value)) {
                continue;
            }
            // 统一大写，比对键与执行侧 valueOf 均不依赖大小写
            LocatorCandidate candidate = new LocatorCandidate();
            candidate.setType(type.toUpperCase());
            candidate.setValue(value);
            Object score = itemMap.get("score");
            if (score instanceof Number) {
                candidate.setScore(((Number) score).doubleValue());
            }
            chain.add(candidate);
        }
        return chain;
    }

    /**
     * 目标目录下按「同名 + 同主定位」查重
     */
    private Element findDuplicate(String projectId, Integer folderId, String name, String locatorType,
                                  String locatorValue) {
        QueryWrapper<Element> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("project_id", projectId);
        queryWrapper.eq("parent_id", folderId);
        queryWrapper.eq("element_type", ElementType.ELEMENT);
        queryWrapper.eq("element_name", name);
        queryWrapper.eq("locator_type", locatorType);
        queryWrapper.eq("locator_value", locatorValue);
        queryWrapper.orderByAsc("sort").orderByAsc("id");
        List<Element> list = elementMapper.selectList(queryWrapper);
        if (list.isEmpty()) {
            return null;
        }
        // 精确比对在 Java 侧做，不依赖数据库 collation（大小写/尾部空格差异不算同一个元素）
        for (Element element : list) {
            String elName = element.getElementName() == null ? null : element.getElementName().trim();
            String elType = element.getLocatorType() == null ? null : element.getLocatorType().toString().trim();
            String elValue = element.getLocatorValue() == null ? null : element.getLocatorValue().trim();
            if (name.equals(elName) && locatorType.equalsIgnoreCase(elType) && locatorValue.equals(elValue)) {
                return element;
            }
        }
        return null;
    }

    private Element createElement(String projectId, Integer folderId, String name, String description,
                                  List<LocatorCandidate> chain) {
        Element element = new Element();
        element.setParentId(folderId);
        element.setElementName(name);
        element.setElementType(ElementType.ELEMENT.name());
        element.setDescription(description);
        element.setProjectId(projectId);
        QueryWrapper<Element> countWrapper = new QueryWrapper<>();
        countWrapper.eq("project_id", projectId);
        countWrapper.eq("parent_id", folderId);
        element.setSort(elementMapper.selectCount(countWrapper).intValue() + 1);
        String userId = StpUtil.getLoginIdAsString();
        element.setCreateUserId(userId);
        element.setUpdateUserId(userId);
        element.setLocatorChain(chain);
        // 链第一项同步为主定位
        ElementProcessor.syncLocatorChain(element);
        elementMapper.insert(element);
        log.info("[ElementSync] 新建库元素: projectId={}, folderId={}, name={}", projectId, folderId, name);
        return element;
    }

    private String toText(Object value) {
        return value == null ? null : value.toString().trim();
    }
}
