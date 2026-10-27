package com.mokatest.platform.demos.element;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mokatest.platform.demos.config.saTokenConfig.ProjectContextHolder;
import com.mokatest.platform.demos.domain.ui.Element;
import com.mokatest.platform.demos.domain.ui.LocatorCandidate;
import com.mokatest.platform.demos.domain.ui.dto.ElementDTO;
import com.mokatest.platform.demos.domain.ui.uiEnum.element.ElementLocatorType;
import com.mokatest.platform.demos.exception.BusinessException;
import com.mokatest.platform.demos.mapper.ElementMapper;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.WaitForSelectorState;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author JingLong
 * @Description 元素工具类
 * @Date 2025/8/17 10:47
 **/
@Slf4j
@Component
public class ElementProcessor {

    /**
     * 定位链单个候选的探测超时（毫秒）：只读探测，超时即尝试下一候选
     */
    private static final double LOCATOR_PROBE_TIMEOUT_MS = 2500;

    @Resource
    private ElementMapper elementMapper;


    /**
     * 是否存在选择元素
     *
     * @param elementDTO
     * @return
     */
    public boolean isExistSelectElement(ElementDTO elementDTO) {
        Element locator = elementDTO.getLocator();
        if (locator == null) {
            return false;
        }
        return !StringUtils.isEmpty(locator.getLocatorType()) && !StringUtils.isEmpty(locator.getLocatorValue());
    }


    /**
     * 是否存在自定义元素
     *
     * @param elementDTO
     * @return
     */
    public boolean isExistCustomElement(ElementDTO elementDTO) {
        Element customLocator = elementDTO.getCustomLocator();
        if (customLocator == null) {
            return false;
        }
        return !StringUtils.isEmpty(customLocator.getLocatorType()) && !StringUtils.isEmpty(customLocator.getLocatorValue());
    }

    /**
     * 存量快照兼容：仅对无 locatorSource 标记的历史数据生效（legacy=true），
     * 无定位链且类型为 TEXT 的步骤内快照来自旧版本，当时 TEXT 是「文本包含」语义，按 TEXT_CONTAINS 处理；
     * 新数据均带显式 locatorSource（前端自定义定位不写链，其 TEXT 为精确匹配语义，不得转换）。
     * 转换前做防御性 copy，避免原地改入参影响调用方复用（步骤重试/AI自愈会再次解析同一 DTO）
     */
    private static Element normalizeLegacySnapshot(Element element, boolean legacy) {
        if (legacy && element != null
                && element.getLocatorType() != null
                && "TEXT".equalsIgnoreCase(element.getLocatorType().toString())
                && (element.getLocatorChain() == null || element.getLocatorChain().isEmpty())) {
            Element copy = copyOf(element);
            copy.setLocatorType("TEXT_CONTAINS");
            return copy;
        }
        return element;
    }

    private static Element copyOf(Element source) {
        Element copy = new Element();
        copy.setId(source.getId());
        copy.setParentId(source.getParentId());
        copy.setElementName(source.getElementName());
        copy.setElementType(source.getElementType());
        copy.setLocatorType(source.getLocatorType());
        copy.setLocatorValue(source.getLocatorValue());
        copy.setLocatorChain(source.getLocatorChain());
        copy.setSort(source.getSort());
        copy.setDescription(source.getDescription());
        copy.setProjectId(source.getProjectId());
        return copy;
    }

    /**
     * 解析步骤实际生效的元素定位。
     * locatorSource=CUSTOM 取自定义，LIBRARY 取库选；为空（历史数据）按「库选优先」规则。
     * 指定来源为空时回退另一侧，两侧都为空时报错。
     */
    public Element getElementLocator(ElementDTO elementDTO) {
        String source = elementDTO.getLocatorSource();
        boolean existSelect = isExistSelectElement(elementDTO);
        boolean existCustom = isExistCustomElement(elementDTO);
        // 无来源标记 = 存量旧数据，TEXT 快照需按旧「包含」语义兼容
        boolean legacy = !StringUtils.hasText(source);

        if ("CUSTOM".equalsIgnoreCase(source)) {
            if (existCustom) {
                return normalizeLegacySnapshot(elementDTO.getCustomLocator(), legacy);
            }
            if (existSelect) {
                return resolveSelectElement(elementDTO, legacy);
            }
        } else if ("LIBRARY".equalsIgnoreCase(source)) {
            if (existSelect) {
                return resolveSelectElement(elementDTO, legacy);
            }
            if (existCustom) {
                return normalizeLegacySnapshot(elementDTO.getCustomLocator(), legacy);
            }
        } else {
            // 历史数据无来源标记：维持「库选优先」规则
            if (existSelect) {
                return resolveSelectElement(elementDTO, legacy);
            }
            if (existCustom) {
                return normalizeLegacySnapshot(elementDTO.getCustomLocator(), legacy);
            }
        }
        throw new BusinessException("元素定位信息缺失");
    }

    /**
     * 库选元素：按 id + projectId 双条件查库中最新的元素信息（强制项目隔离），
     * 查不到（已删除或不属于当前项目）按「元素已删除」回退步骤内快照
     */
    private Element resolveSelectElement(ElementDTO elementDTO, boolean legacy) {
        Element snapshot = normalizeLegacySnapshot(elementDTO.getLocator(), legacy);
        Integer id = snapshot.getId();
        if (id == null) {
            return snapshot;
        }
        String projectId = resolveContextProjectId(snapshot);
        Element element;
        if (StringUtils.hasText(projectId)) {
            element = elementMapper.selectOne(new LambdaQueryWrapper<Element>()
                    .eq(Element::getId, id)
                    .eq(Element::getProjectId, projectId));
        } else {
            // 上下文取不到 projectId（如计划执行线程）时保持原有按 id 查询
            element = elementMapper.selectById(id);
        }
        if (element == null) {
            log.warn("[Element] 库元素 id={} 已删除或不属于当前项目，回退步骤内快照", id);
            return snapshot;
        }
        return element;
    }

    /**
     * 项目隔离用的 projectId：优先步骤快照自带的项目ID，其次请求/执行上下文中的项目ID
     */
    private String resolveContextProjectId(Element snapshot) {
        if (StringUtils.hasText(snapshot.getProjectId())) {
            return snapshot.getProjectId();
        }
        Integer ctxProjectId = ProjectContextHolder.getProjectId();
        return ctxProjectId != null ? String.valueOf(ctxProjectId) : null;
    }

    /**
     * 取元素的定位候选链：链为空时由单定位（locatorType/locatorValue）合成单元素链，
     * 保证存量无链数据与步骤内快照都能走链式流程
     */
    public static List<LocatorCandidate> resolveChain(Element element) {
        List<LocatorCandidate> chain = new ArrayList<>();
        if (element == null) {
            return chain;
        }
        if (element.getLocatorChain() != null && !element.getLocatorChain().isEmpty()) {
            for (LocatorCandidate candidate : element.getLocatorChain()) {
                if (candidate != null && StringUtils.hasText(candidate.getType())
                        && StringUtils.hasText(candidate.getValue())) {
                    chain.add(candidate);
                }
            }
        }
        if (chain.isEmpty() && element.getLocatorType() != null
                && StringUtils.hasText(element.getLocatorValue())) {
            LocatorCandidate candidate = new LocatorCandidate();
            candidate.setType(element.getLocatorType().toString());
            candidate.setValue(element.getLocatorValue());
            chain.add(candidate);
        }
        return chain;
    }

    /**
     * 写入前的读写兼容同步：
     * 链非空 → 链第一项同步为主定位（locatorType/locatorValue）；
     * 链为空但有单定位 → 合成单元素链
     */
    public static void syncLocatorChain(Element element) {
        if (element == null) {
            return;
        }
        List<LocatorCandidate> chain = resolveChain(element);
        if (!chain.isEmpty()) {
            LocatorCandidate first = chain.get(0);
            element.setLocatorType(first.getType());
            element.setLocatorValue(first.getValue());
            element.setLocatorChain(chain);
        }
    }

    /**
     * 按定位链解析 Playwright Locator：按序逐个短超时只读探测（附着且唯一），命中即用；
     * 全部失败返回主定位，由步骤自身超时与 AI 自愈兜底。对外仍只返回单个 Locator。
     */
    public Locator resolveLocator(Frame frame, ElementDTO elementDTO) {
        return resolveLocator(frame, getElementLocator(elementDTO));
    }

    /**
     * 按定位链解析 Playwright Locator（已解析出生效元素的场景：断言/提取等无 DTO 的调用方）
     */
    public static Locator resolveLocator(Frame frame, Element element) {
        List<LocatorCandidate> chain = resolveChain(element);
        if (chain.isEmpty()) {
            throw new BusinessException("元素定位信息缺失");
        }
        // 单候选（存量无链数据合成的单元素链）直接返回，不做无意义探测
        if (chain.size() == 1) {
            return buildLocator(frame, chain.get(0));
        }
        Locator primary = null;
        List<String> probeSummary = new ArrayList<>();
        for (int i = 0; i < chain.size(); i++) {
            LocatorCandidate candidate = chain.get(i);
            Locator locator;
            try {
                locator = buildLocator(frame, candidate);
            } catch (Exception e) {
                // 脏数据（如 DB 枚举残留的非法 type）不中止整条链，跳过继续下一候选
                log.warn("[LocatorChain] 链第 {} 项定位构建失败，跳过: {}={}, 原因: {}",
                        i + 1, candidate.getType(), candidate.getValue(), e.getMessage());
                probeSummary.add(candidate.getType() + "=" + candidate.getValue() + "(构建失败)");
                continue;
            }
            if (primary == null) {
                primary = locator;
            }
            if (probeCandidate(locator)) {
                if (i > 0) {
                    log.info("[LocatorChain] 主定位未命中，命中链第 {} 项: {}={}",
                            i + 1, candidate.getType(), candidate.getValue());
                }
                return locator;
            }
            probeSummary.add(candidate.getType() + "=" + candidate.getValue() + "(未命中)");
        }
        log.warn("[LocatorChain] 定位链 {} 个候选均未命中，返回主定位走原有超时/AI自愈流程。探测摘要: {}",
                chain.size(), String.join("; ", probeSummary));
        if (primary == null) {
            throw new BusinessException("元素定位信息缺失");
        }
        return primary;
    }

    private static Locator buildLocator(Frame frame, LocatorCandidate candidate) {
        return ElementLocatorProcessor.process(frame,
                ElementLocatorType.valueOf(candidate.getType().toUpperCase()),
                candidate.getValue());
    }

    /**
     * 只读探测：等待候选附着到 DOM（短超时）后立即计数，要求唯一匹配；不改变页面状态。
     * 可见性交给步骤自身 actionability 检查（setInputFiles 等操作作用于 display:none 元素是合法的）
     */
    private static boolean probeCandidate(Locator locator) {
        try {
            locator.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.ATTACHED)
                    .setTimeout(LOCATOR_PROBE_TIMEOUT_MS));
            return locator.count() == 1;
        } catch (Exception e) {
            return false;
        }
    }
}
