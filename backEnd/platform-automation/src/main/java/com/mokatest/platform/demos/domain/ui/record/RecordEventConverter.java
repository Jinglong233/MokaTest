package com.mokatest.platform.demos.domain.ui.record;

import com.mokatest.platform.demos.domain.ui.Element;
import com.mokatest.platform.demos.domain.ui.Setting;
import com.mokatest.platform.demos.domain.ui.dto.ElementDTO;
import com.mokatest.platform.demos.domain.ui.dto.record.RecordCandidateDTO;
import com.mokatest.platform.demos.domain.ui.dto.record.RecordElementDTO;
import com.mokatest.platform.demos.domain.ui.dto.record.RecordEventDTO;
import com.mokatest.platform.demos.domain.ui.dto.record.RecordFileDTO;
import com.mokatest.platform.demos.domain.ui.dto.record.RecordStepDraftVO;
import com.mokatest.platform.demos.domain.ui.dto.step.ClickStepDTO;
import com.mokatest.platform.demos.domain.ui.dto.step.ClosePageStepDTO;
import com.mokatest.platform.demos.domain.ui.dto.step.IframeStepDTO;
import com.mokatest.platform.demos.domain.ui.dto.step.KeyboardStepDTO;
import com.mokatest.platform.demos.domain.ui.dto.step.OpenPageStepDTO;
import com.mokatest.platform.demos.domain.ui.uiEnum.browser.ColsePageMode;
import com.mokatest.platform.demos.domain.ui.uiEnum.click.ClickType;
import com.mokatest.platform.demos.domain.ui.uiEnum.iframe.SwitchIframeType;
import com.mokatest.platform.demos.domain.ui.uiEnum.keyboard.KeyboardInputType;
import com.mokatest.platform.demos.domain.ui.uiEnum.keyboard.KeyboardKey;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 插件录制事件 → 平台步骤 DTO 转换器
 */
@Component
public class RecordEventConverter {

    @Resource
    private ElementLibraryMatcher elementLibraryMatcher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 转换整个录制文件
     */
    public RecordConvertResult convert(RecordFileDTO file, Integer projectId) {
        List<RecordStepDraftVO> steps = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Integer> skipped = new HashMap<>();
        skipped.put("iframeEvents", 0);

        List<RecordEventDTO> events = new ArrayList<>(file.getEvents());
        // 按 seq 排序，seq 为空则保持原顺序
        events.sort((a, b) -> {
            if (a.getSeq() == null && b.getSeq() == null) return 0;
            if (a.getSeq() == null) return 1;
            if (b.getSeq() == null) return -1;
            return Integer.compare(a.getSeq(), b.getSeq());
        });
        for (int i = 0; i < events.size(); i++) {
            RecordEventDTO event = events.get(i);
            try {
                convertEvent(event, projectId, steps, warnings, skipped, i + 1);
            } catch (Exception e) {
                warnings.add("第 " + (i + 1) + " 个事件转换失败：" + e.getMessage());
            }
        }

        RecordConvertResult result = new RecordConvertResult();
        result.setSteps(steps);
        result.setWarnings(warnings);
        result.setSkipped(skipped);
        return result;
    }

    private void convertEvent(RecordEventDTO event, Integer projectId,
                              List<RecordStepDraftVO> steps, List<String> warnings,
                              Map<String, Integer> skipped, int index) {
        String action = event.getAction();
        if (action == null) {
            warnings.add("第 " + index + " 个事件 action 为空，已跳过");
            return;
        }

        switch (action) {
            case "OPEN_URL":
                handleOpenUrl(event, steps);
                break;
            case "CLICK":
            case "DBLCLICK":
            case "RIGHT_CLICK":
                handleClick(event, projectId, steps, warnings, index, action);
                break;
            case "SELECT":
                handleSelect(event, projectId, steps, warnings, index);
                break;
            case "INPUT":
                handleInput(event, projectId, steps, warnings, index);
                break;
            case "KEY_PRESS":
                handleKeyPress(event, steps);
                break;
            case "NAVIGATE":
                handleNavigate(steps);
                break;
            case "IFRAME_ENTER":
                handleIframeEnter(event, projectId, steps, warnings, index);
                break;
            case "IFRAME_EXIT":
                handleIframeExit(steps);
                break;
            case "CLOSE_PAGE":
                handleClosePage(event, steps);
                break;
            default:
                warnings.add("第 " + index + " 个事件 action=" + action + " 暂不支持的转换，已跳过");
        }
    }

    private void handleOpenUrl(RecordEventDTO event, List<RecordStepDraftVO> steps) {
        String stepName = "打开页面 " + truncate(event.getUrl(), 40);
        OpenPageStepDTO dto = new OpenPageStepDTO();
        dto.setStepType("OPEN_PAGE");
        dto.setStepName(stepName);
        dto.setUrl(event.getUrl());
        dto.setRecover(1);
        dto.setSetting(createDefaultSetting());
        steps.add(buildDraft(stepName, "OPEN_PAGE",
                toStepDetailMap(dto, null), false));
    }

    private void handleClick(RecordEventDTO event, Integer projectId,
                             List<RecordStepDraftVO> steps, List<String> warnings,
                             int index, String action) {
        String elementName = getElementName(event.getElement());
        String stepName = resolveClickLabel(action) + " " + elementName;
        ClickStepDTO dto = new ClickStepDTO();
        dto.setStepType("CLICK");
        dto.setStepName(stepName);
        dto.setClickType(resolveClickType(action));
        dto.setElement(buildElementDTO(event.getElement(), projectId, warnings, index));
        dto.setSetting(createDefaultSetting());
        steps.add(buildDraft(stepName, "CLICK",
                toStepDetailMap(dto, event.getElement() != null ? event.getElement().getCandidates() : null), false));
    }

    private void handleSelect(RecordEventDTO event, Integer projectId,
                              List<RecordStepDraftVO> steps, List<String> warnings,
                              int index) {
        String elementName = getElementName(event.getElement());
        String stepName = "选择 " + elementName;
        ClickStepDTO dto = new ClickStepDTO();
        dto.setStepType("CLICK");
        dto.setStepName(stepName);
        dto.setClickType(ClickType.SELECT);
        dto.setElement(buildElementDTO(event.getElement(), projectId, warnings, index));
        dto.setOptionValue(event.getValueText());
        dto.setSetting(createDefaultSetting());
        steps.add(buildDraft(stepName, "CLICK",
                toStepDetailMap(dto, event.getElement() != null ? event.getElement().getCandidates() : null), false));
    }

    private void handleInput(RecordEventDTO event, Integer projectId,
                             List<RecordStepDraftVO> steps, List<String> warnings,
                             int index) {
        String elementName = getElementName(event.getElement());
        String stepName = "输入 " + elementName;
        KeyboardStepDTO dto = new KeyboardStepDTO();
        dto.setStepType("KEYBOARD");
        dto.setStepName(stepName);
        dto.setInputType(KeyboardInputType.NORMAL);
        dto.setElement(buildElementDTO(event.getElement(), projectId, warnings, index));
        dto.setInputValue(event.getValue());
        dto.setIsAdditional(0);
        dto.setSetting(createDefaultSetting());
        steps.add(buildDraft(stepName, "KEYBOARD",
                toStepDetailMap(dto, event.getElement() != null ? event.getElement().getCandidates() : null),
                Boolean.TRUE.equals(event.getIsPassword())));
    }

    private void handleKeyPress(RecordEventDTO event, List<RecordStepDraftVO> steps) {
        String stepName = "按下 " + (event.getKey() != null ? event.getKey() : "按键");
        KeyboardStepDTO dto = new KeyboardStepDTO();
        dto.setStepType("KEYBOARD");
        dto.setStepName(stepName);
        dto.setInputType(KeyboardInputType.KEYBOARD);
        dto.setKeyboardKey(resolveKeyboardKey(event.getKey()));
        dto.setIsAdditional(0);
        dto.setSetting(createDefaultSetting());
        steps.add(buildDraft(stepName, "KEYBOARD",
                toStepDetailMap(dto, null), false));
    }

    private void handleNavigate(List<RecordStepDraftVO> steps) {
        // 整页跳转不再生成等待步骤：Playwright 动作自带自动等待，
        // 如需等待可在场景/步骤设置中配置执行后等待时间
    }

    private void handleIframeEnter(RecordEventDTO event, Integer projectId,
                                   List<RecordStepDraftVO> steps, List<String> warnings,
                                   int index) {
        IframeStepDTO dto = new IframeStepDTO();
        dto.setStepType("IFRAME");
        SwitchIframeType switchType = resolveSwitchIframeType(event.getSwitchIframeType());
        dto.setSwitchIframeType(switchType);

        switch (switchType) {
            case ELEMENT:
                dto.setElement(buildElementDTO(event.getElement(), projectId, warnings, index));
                break;
            case URL:
                dto.setUrl(event.getUrl());
                break;
            case NAME:
                dto.setIframeName(event.getIframeName());
                break;
            case ID:
                dto.setIframeId(event.getIframeId());
                break;
            case INDEX:
                dto.setIframeIndex(event.getIframeIndex());
                break;
            default:
                // EXIT 不会出现在 IFRAME_ENTER，其他类型无需额外字段
                break;
        }

        dto.setSetting(createDefaultSetting());
        String label = "进入 iframe";
        if (StringUtils.hasText(event.getIframeName())) {
            label += " (" + truncate(event.getIframeName(), 20) + ")";
        }
        dto.setStepName(label);
        steps.add(buildDraft(label, "IFRAME",
                toStepDetailMap(dto, event.getElement() != null ? event.getElement().getCandidates() : null), false));
    }

    private void handleIframeExit(List<RecordStepDraftVO> steps) {
        String stepName = "返回主页面";
        IframeStepDTO dto = new IframeStepDTO();
        dto.setStepType("IFRAME");
        dto.setStepName(stepName);
        dto.setSwitchIframeType(SwitchIframeType.EXIT);
        dto.setSetting(createDefaultSetting());
        steps.add(buildDraft(stepName, "IFRAME", toStepDetailMap(dto, null), false));
    }

    private void handleClosePage(RecordEventDTO event, List<RecordStepDraftVO> steps) {
        String stepName = "关闭页面";
        ClosePageStepDTO dto = new ClosePageStepDTO();
        dto.setStepType("CLOSE_PAGE");
        dto.setStepName(stepName);
        dto.setClosePageMode(resolveClosePageMode(event.getClosePageMode()));
        dto.setCustomIndex(event.getCustomIndex());
        dto.setSetting(createDefaultSetting());
        steps.add(buildDraft(stepName, "CLOSE_PAGE", toStepDetailMap(dto, null), false));
    }

    private Setting createDefaultSetting() {
        Setting setting = new Setting();
        setting.setIsSetting(0);
        setting.setPreExecuteWaitingTime(0);
        setting.setWaitingTimeAfterExecution(0);
        setting.setTimeout(0);
        setting.setPauseTimeout(0);
        setting.setErrorHandlingStrategy(com.mokatest.platform.demos.domain.ui.uiEnum.StepErrorHandleStrategy.IGNORE);
        setting.setScreenshotConfiguration(com.mokatest.platform.demos.domain.ui.uiEnum.ScreenshotConfig.NOT_SCREENSHOT);
        setting.setPageInformation("");
        return setting;
    }

    private RecordStepDraftVO buildDraft(String stepName, String stepType, Object stepDetail, Boolean isPassword) {
        RecordStepDraftVO draft = new RecordStepDraftVO();
        draft.setStepName(stepName);
        draft.setStepType(stepType);
        draft.setStepDetail(stepDetail);
        draft.setIsPassword(isPassword);
        return draft;
    }

    private ElementDTO buildElementDTO(RecordElementDTO element, Integer projectId,
                                       List<String> warnings, int index) {
        if (element == null) {
            return null;
        }
        if (element.getCandidates() == null || element.getCandidates().isEmpty()) {
            warnings.add("第 " + index + " 个事件缺少元素定位候选");
            return null;
        }

        RecordCandidateDTO firstValid = null;
        Element matched = null;
        for (RecordCandidateDTO candidate : element.getCandidates()) {
            if (!StringUtils.hasText(candidate.getLocatorType()) || !StringUtils.hasText(candidate.getLocatorValue())) {
                continue;
            }
            if (firstValid == null) {
                firstValid = candidate;
            }
            Element lib = elementLibraryMatcher.match(projectId, candidate);
            if (lib != null) {
                matched = lib;
                break;
            }
        }

        if (firstValid == null) {
            warnings.add("第 " + index + " 个事件没有有效定位候选");
            return null;
        }

        ElementDTO dto = new ElementDTO();
        if (matched != null) {
            Element locator = new Element();
            locator.setId(matched.getId());
            locator.setElementName(matched.getElementName());
            locator.setLocatorType(matched.getLocatorType());
            locator.setLocatorValue(matched.getLocatorValue());
            dto.setLocator(locator);
            // 同时给 customLocator 一个空对象，避免前端 ElementDTO 解析后缺字段
            dto.setCustomLocator(new Element());
        } else {
            Element custom = new Element();
            custom.setElementName(element.getElementName());
            custom.setLocatorType(firstValid.getLocatorType());
            custom.setLocatorValue(firstValid.getLocatorValue());
            dto.setCustomLocator(custom);
            // 同时给 locator 一个空对象
            dto.setLocator(new Element());
        }
        return dto;
    }

    private Map<String, Object> toStepDetailMap(Object dto, List<RecordCandidateDTO> candidates) {
        Map<String, Object> map = objectMapper.convertValue(dto, new TypeReference<Map<String, Object>>() {
        });
        if (candidates != null && !candidates.isEmpty()) {
            map.put("locatorCandidates", candidates);
        }
        map.put("recorded", true);
        return map;
    }

    private ClickType resolveClickType(String action) {
        if ("DBLCLICK".equals(action)) {
            return ClickType.DOUBLE_CLICK;
        }
        if ("RIGHT_CLICK".equals(action)) {
            return ClickType.RIGHT_CLICK;
        }
        return ClickType.SINGLE_CLICK;
    }

    private String resolveClickLabel(String action) {
        if ("DBLCLICK".equals(action)) {
            return "双击";
        }
        if ("RIGHT_CLICK".equals(action)) {
            return "右键";
        }
        return "点击";
    }

    private KeyboardKey resolveKeyboardKey(String key) {
        if ("ENTER".equalsIgnoreCase(key)) {
            return KeyboardKey.ENTER;
        }
        return KeyboardKey.PRESS_INPUT;
    }

    private SwitchIframeType resolveSwitchIframeType(String type) {
        if (!StringUtils.hasText(type)) {
            return SwitchIframeType.URL;
        }
        try {
            return SwitchIframeType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return SwitchIframeType.URL;
        }
    }

    private ColsePageMode resolveClosePageMode(String mode) {
        if (!StringUtils.hasText(mode)) {
            return ColsePageMode.CURRENT;
        }
        try {
            return ColsePageMode.valueOf(mode);
        } catch (IllegalArgumentException e) {
            return ColsePageMode.CURRENT;
        }
    }

    private String getElementName(RecordElementDTO element) {
        if (element == null) {
            return "未知元素";
        }
        return StringUtils.hasText(element.getElementName()) ? element.getElementName() : "未知元素";
    }

    private String truncate(String str, int maxLen) {
        if (str == null) {
            return "";
        }
        return str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }

}
