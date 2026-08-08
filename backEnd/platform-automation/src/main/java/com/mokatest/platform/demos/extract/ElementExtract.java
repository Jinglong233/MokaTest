package com.mokatest.platform.demos.extract;

import com.mokatest.platform.demos.domain.ui.uiEnum.element.ElementLocatorType;
import com.mokatest.platform.demos.domain.ui.uiEnum.extract.ElementExtractValueType;
import com.mokatest.platform.demos.domain.ui.uiEnum.extract.ExtractType;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.domain.ui.Element;
import com.mokatest.platform.demos.domain.ui.dto.ElementDTO;
import com.mokatest.platform.demos.domain.ui.dto.step.ExtractStepDTO;
import com.mokatest.platform.demos.element.ElementLocatorProcessor;
import com.mokatest.platform.demos.element.ElementProcessor;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Locator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.HashMap;

/**
 * @Author JingLong
 * @Description 元素提取
 * @Date 2025/7/21 20:35
 **/
@Repository
public class ElementExtract implements AssociationExtraction {


    @Resource
    private ElementProcessor elementProcessor;

    @Override
    public Object extract(TestExecutionContext context, ExtractStepDTO extractorInfo) {
        String variableName = null;
        String extractValue = null;
        // 获取变量名称
        variableName = extractorInfo.getVariableName();
        // 获取元素的处理方式
        Object elementType = extractorInfo.getElementExtractType();
        ElementExtractValueType elementExtractValueType = ElementExtractValueType.valueOf(elementType.toString());
        ElementDTO element = extractorInfo.getElement();
        // 获取元素选择器
        Element element1 = elementProcessor.getElementLocator(element);

        Frame currentFrame = context.getCurrentFrame();
        Locator elementLocator = ElementLocatorProcessor.process(currentFrame,
                ElementLocatorType.valueOf(element1.getLocatorType().toString().toUpperCase()),
                element1.getLocatorValue());


        extractValue = "";
        switch (elementExtractValueType) {
            case TEXT -> {
                String tagName = elementLocator.evaluate("el => el.tagName.toLowerCase()").toString();
                // 根据标签获取文本内容
                switch (tagName) {
                    case "input", "textarea","select" -> extractValue = elementLocator.inputValue();
                    default -> extractValue = elementLocator.textContent();
                }

            }
            case ATTRIBUTE -> {
                String attributeName = extractorInfo.getElementAttribute().toLowerCase();
                extractValue = elementLocator.getAttribute(attributeName);
            }
            case HTML -> {
                extractValue = elementLocator.evaluate("element => element.outerHTML").toString();
                }
            case VALUE -> {
                extractValue = elementLocator.getAttribute("value");
            }
            case COUNT -> {
                int count = elementLocator.count();
                extractValue = String.valueOf(count);
            }
        }
        // 将抽取的内容存进上下文临时变量表
        context.getVariables().put(variableName, extractValue == null ? "" : extractValue);
        HashMap<Object, Object> map = new HashMap<>();
        if (extractValue==null) extractValue = "";
        map.put(variableName, extractValue);
        return map;
    }

    @Override
    public boolean isSupport(ExtractType extractType) {
        return ExtractType.ELEMENT.equals(extractType);
    }
}
