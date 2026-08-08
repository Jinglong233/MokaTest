package com.mokatest.platform.demos.result;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType;
import lombok.Data;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class BaseStepResult {
    // 耗时（前置+主步骤+后置步骤）
    private double timeConsuming;
    private String errorMessage;
    private String screenshotPath;
    private String pageUrl;
    private String frameUrl;
    private String additionalInfo;

    private StepExecutionType status;

    // API 请求步骤（UI+API 混合场景）的响应 VO（ApiStepResponseVO），其他步骤类型为 null
    private Object apiResponse;


    // 断言列表操作的结果
    private Map<Integer, AssertResult> assertResults = new LinkedHashMap<>();

    // 抽取列表操作的结果
    private Map<Object, Object> extractResults = new LinkedHashMap<>();


    // while循环条件专用的断言结果列表
    private Map<Integer, Map<Integer, AssertResult>> whileAssertResults = new HashMap<>();

    // Extract抽取操作的结果
    private Map<String, String> extractResultsList = new HashMap<>();


    public BaseStepResult() {
        this.status = StepExecutionType.SKIPPED;
    }



}