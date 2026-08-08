package com.mokatest.platform.demos.domain.ui.record;

import com.mokatest.platform.demos.domain.ui.dto.record.RecordCandidateDTO;
import com.mokatest.platform.demos.domain.ui.dto.record.RecordElementDTO;
import com.mokatest.platform.demos.domain.ui.dto.record.RecordEventDTO;
import com.mokatest.platform.demos.domain.ui.dto.record.RecordFileDTO;
import com.mokatest.platform.demos.domain.ui.dto.record.RecordStepDraftVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordEventConverterTest {

    @Test
    void testConvertFullFlow() {
        RecordEventConverter converter = new RecordEventConverter();
        // 不依赖元素库，全部走 customLocator
        ElementLibraryMatcher matcher = mock(ElementLibraryMatcher.class);
        ReflectionTestUtils.setField(converter, "elementLibraryMatcher", matcher);

        RecordFileDTO file = new RecordFileDTO();
        file.setVersion("1.0");
        file.setEvents(Arrays.asList(
                buildEvent("OPEN_URL", "https://example.com", null, null),
                buildEvent("INPUT", null, buildElement("用户名", "TEST_ID", "user-name"), "admin"),
                buildEvent("CLICK", null, buildElement("登录", "TEXT", "登录"), null),
                buildEvent("NAVIGATE", "https://example.com/home", null, null),
                buildEvent("NAVIGATE", "https://example.com/dashboard", null, null),
                buildIframeEnter("URL", "https://example.com/iframe", null),
                buildEvent("IFRAME_EXIT", null, null, null),
                buildEvent("CLOSE_PAGE", null, null, null)
        ));

        RecordConvertResult result = converter.convert(file, 1);
        List<RecordStepDraftVO> steps = result.getSteps();

        // OPEN_URL + INPUT + CLICK + IFRAME_ENTER + IFRAME_EXIT + CLOSE_PAGE = 6（NAVIGATE 不再生成 WAIT 步骤）
        assertEquals(6, steps.size(), "步骤数不正确");

        assertEquals("OPEN_PAGE", steps.get(0).getStepType());
        assertEquals("打开页面 https://example.com", steps.get(0).getStepName());
        assertEquals("KEYBOARD", steps.get(1).getStepType());
        assertEquals("输入 用户名", steps.get(1).getStepName());
        assertEquals("CLICK", steps.get(2).getStepType());
        assertEquals("IFRAME", steps.get(3).getStepType());
        assertEquals("IFRAME", steps.get(4).getStepType());
        assertEquals("CLOSE_PAGE", steps.get(5).getStepType());

        // 检查 INPUT 保留了输入值
        Map<String, Object> inputDetail = (Map<String, Object>) steps.get(1).getStepDetail();
        assertEquals("admin", inputDetail.get("inputValue"));

        // 检查 IFRAME_ENTER URL
        Map<String, Object> iframeDetail = (Map<String, Object>) steps.get(3).getStepDetail();
        assertEquals("URL", iframeDetail.get("switchIframeType"));
        assertEquals("https://example.com/iframe", iframeDetail.get("url"));

        // 检查 IFRAME_EXIT
        Map<String, Object> exitDetail = (Map<String, Object>) steps.get(4).getStepDetail();
        assertEquals("EXIT", exitDetail.get("switchIframeType"));

        // 每个步骤都应包含默认 setting
        for (RecordStepDraftVO step : steps) {
            Map<String, Object> detail = (Map<String, Object>) step.getStepDetail();
            assertNotNull(detail.get("setting"), "步骤缺少 setting");
            assertTrue(Boolean.parseBoolean(String.valueOf(detail.get("recorded"))), "步骤应标记为 recorded");
        }
    }

    private RecordEventDTO buildEvent(String action, String url, RecordElementDTO element, String value) {
        RecordEventDTO event = new RecordEventDTO();
        event.setAction(action);
        event.setUrl(url);
        event.setElement(element);
        event.setValue(value);
        return event;
    }

    private RecordElementDTO buildElement(String elementName, String locatorType, String locatorValue) {
        RecordElementDTO element = new RecordElementDTO();
        element.setElementName(elementName);
        RecordCandidateDTO candidate = new RecordCandidateDTO();
        candidate.setLocatorType(locatorType);
        candidate.setLocatorValue(locatorValue);
        candidate.setScore(100);
        element.setCandidates(new ArrayList<>(Arrays.asList(candidate)));
        return element;
    }

    private RecordEventDTO buildIframeEnter(String switchType, String url, RecordElementDTO element) {
        RecordEventDTO event = new RecordEventDTO();
        event.setAction("IFRAME_ENTER");
        event.setSwitchIframeType(switchType);
        event.setUrl(url);
        event.setElement(element);
        return event;
    }

}
