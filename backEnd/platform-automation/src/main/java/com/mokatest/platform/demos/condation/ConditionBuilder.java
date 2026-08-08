package com.mokatest.platform.demos.condation;

import com.mokatest.platform.demos.domain.ui.uiEnum.condation.AssertRelationship;
import com.mokatest.platform.demos.domain.ui.uiEnum.condation.AssertType;
import com.mokatest.platform.demos.domain.ui.Element;
import com.mokatest.platform.demos.domain.ui.dto.step.AssertStepDTO;
import com.mokatest.platform.demos.element.ElementProcessor;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author JingLong
 * @Description 条件构造器
 * @Date 2025/7/20 11:12
 **/
@Component
public class ConditionBuilder implements Serializable {


    private static final long serialVersionUID = -2505184375607226158L;
    @Resource
    private ElementProcessor elementProcessor;


    public List<TestCondition> loadCondition(List<AssertStepDTO> conditions) {
        if (conditions == null || conditions.isEmpty()) return new ArrayList<>();

        List<TestCondition> resultConditions = new ArrayList<>();
        // 获取条件相关的参数
        for (AssertStepDTO assertStepDTO : conditions) {
            // 根据不同断言类型获取不同参数
            AssertType assertType = AssertType.valueOf(assertStepDTO.getAssertType());
            switch (assertType) {
                case ELEMENT_EXIST, ELEMENT_NOT_EXIST -> {
                    Element element = elementProcessor.getElementLocator(assertStepDTO.getElement());
                    ElementCondition condition = new ElementCondition();
                    condition.setAssertType(assertType);
                    condition.setElement(element);
                    resultConditions.add(condition);
                }
                case TEXT_EXIST, TEXT_NOT_EXIST -> {
                    String assertText = assertStepDTO.getAssertText();
                    TextCondition textCondition = new TextCondition(assertType, assertText);
                    textCondition.setAssertType(assertType);
                    resultConditions.add(textCondition);
                }
                case ELEMENT_ARRTRIBUTE -> {
                    Element element = elementProcessor.getElementLocator(assertStepDTO.getElement());
                    ElementCondition elementCondition = new ElementCondition();
                    elementCondition.setElement(element);
                    elementCondition.setAssertType(assertType);
                    elementCondition.setAttributeName(assertStepDTO.getElementAttribute().toUpperCase());
                    elementCondition.setExpectedValue(assertStepDTO.getExceptValue());
                    elementCondition.setAssertRelationship(AssertRelationship.valueOf(assertStepDTO.getAssertRelationship().toString()));
                    resultConditions.add(elementCondition);

                }
                case PAGE_ARRTRIBUTE -> {
                    PageCondition pageCondition = new PageCondition();
                    pageCondition.setAssertType(assertType);
                    pageCondition.setExpectedValue(assertStepDTO.getExceptValue());
                    pageCondition.setAttributeName(assertStepDTO.getPageAttribute().toString().toUpperCase());
                    pageCondition.setAssertRelationship(AssertRelationship.valueOf(assertStepDTO.getAssertRelationship().toString()));
                    resultConditions.add(pageCondition);
                }
                case CUSTOM -> {
                    CustomCondition customCondition = new CustomCondition();
                    customCondition.setAssertType(assertType);
                    customCondition.setExpectedValue(assertStepDTO.getExceptValue());
                    customCondition.setAssertText(assertStepDTO.getAssertText());
                    customCondition.setAssertRelationship(AssertRelationship.valueOf(assertStepDTO.getAssertRelationship().toString()));
                    resultConditions.add(customCondition);
                }
            }
        }
        return resultConditions;
    }


}
