package com.mokatest.platform.demos.domain.ui.record;

import com.mokatest.platform.demos.domain.ui.dto.record.RecordCandidateDTO;
import com.mokatest.platform.demos.domain.ui.dto.record.RecordElementDTO;
import com.mokatest.platform.demos.domain.ui.dto.record.RecordEventDTO;
import com.mokatest.platform.demos.domain.ui.dto.record.RecordFileDTO;
import com.mokatest.platform.demos.domain.ui.uiEnum.element.ElementLocatorType;
import com.mokatest.platform.demos.exception.BusinessException;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 录制 JSON 文件校验器
 */
public class RecordFileValidator {

    private static final Set<String> ALLOWED_ACTIONS = new HashSet<>(Arrays.asList(
            "OPEN_URL", "CLICK", "DBLCLICK", "RIGHT_CLICK", "INPUT", "SELECT",
            "KEY_PRESS", "NAVIGATE", "IFRAME_ENTER", "IFRAME_EXIT", "CLOSE_PAGE"
    ));

    private static final Set<String> ALLOWED_LOCATOR_TYPES = new HashSet<>();

    static {
        for (ElementLocatorType type : ElementLocatorType.values()) {
            ALLOWED_LOCATOR_TYPES.add(type.name());
        }
    }

    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024;
    private static final int MAX_EVENTS = 500;
    private static final int MAX_URL_LENGTH = 2048;
    private static final int MAX_VALUE_LENGTH = 1024;
    private static final int MAX_LOCATOR_VALUE_LENGTH = 1024;

    /**
     * 校验上传文件
     */
    public static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("录制文件大小超过 2MB 上限");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".json")) {
            throw new BusinessException("请上传 .json 格式的录制文件");
        }
    }

    /**
     * 校验解析后的录制文件内容
     */
    public static void validateContent(RecordFileDTO fileDTO) {
        if (fileDTO == null) {
            throw new BusinessException("录制文件内容为空");
        }
        if (!"1.0".equals(fileDTO.getVersion())) {
            throw new BusinessException("不支持的录制文件版本：" + fileDTO.getVersion() + "，请升级插件");
        }
        if (CollectionUtils.isEmpty(fileDTO.getEvents())) {
            throw new BusinessException("录制文件中没有事件");
        }
        if (fileDTO.getEvents().size() > MAX_EVENTS) {
            throw new BusinessException("录制事件数超过 500 条上限");
        }

        List<RecordEventDTO> events = fileDTO.getEvents();
        for (int i = 0; i < events.size(); i++) {
            validateEvent(events.get(i), i + 1);
        }
    }

    private static void validateEvent(RecordEventDTO event, int index) {
        if (event == null) {
            throw new BusinessException("第 " + index + " 个事件为空");
        }
        if (!StringUtils.hasText(event.getAction())) {
            throw new BusinessException("第 " + index + " 个事件缺少 action 字段");
        }
        if (!ALLOWED_ACTIONS.contains(event.getAction())) {
            throw new BusinessException("第 " + index + " 个事件包含不支持的 action：" + event.getAction());
        }
        if (event.getElement() != null) {
            validateElement(event.getElement(), index);
        }
        if (StringUtils.hasText(event.getUrl()) && event.getUrl().length() > MAX_URL_LENGTH) {
            throw new BusinessException("第 " + index + " 个事件的 url 超过 2048 字符");
        }
        if (StringUtils.hasText(event.getValue()) && event.getValue().length() > MAX_VALUE_LENGTH) {
            throw new BusinessException("第 " + index + " 个事件的 value 超过 1024 字符");
        }
    }

    private static void validateElement(RecordElementDTO element, int index) {
        if (element == null) {
            return;
        }
        if (element.getElementName() != null && element.getElementName().length() > 256) {
            throw new BusinessException("第 " + index + " 个事件的元素名称超过 256 字符");
        }
        if (CollectionUtils.isEmpty(element.getCandidates())) {
            return;
        }
        for (int i = 0; i < element.getCandidates().size(); i++) {
            RecordCandidateDTO candidate = element.getCandidates().get(i);
            if (candidate == null) {
                continue;
            }
            if (!StringUtils.hasText(candidate.getLocatorType())) {
                throw new BusinessException("第 " + index + " 个事件的第 " + (i + 1) + " 个候选缺少 locatorType");
            }
            if (!ALLOWED_LOCATOR_TYPES.contains(candidate.getLocatorType())) {
                throw new BusinessException("第 " + index + " 个事件的候选定位类型不合法：" + candidate.getLocatorType());
            }
            if (StringUtils.hasText(candidate.getLocatorValue())
                    && candidate.getLocatorValue().length() > MAX_LOCATOR_VALUE_LENGTH) {
                throw new BusinessException("第 " + index + " 个事件的候选定位值超过 1024 字符");
            }
        }
    }

    /**
     * 读取文件内容为字符串
     */
    public static String readFileContent(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException("读取录制文件失败：" + e.getMessage());
        }
    }

}
