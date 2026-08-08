package com.mokatest.platform.demos.extract;

import com.mokatest.platform.demos.domain.ui.uiEnum.extract.ExtractType;
import com.mokatest.platform.demos.domain.ui.uiEnum.extract.PageExtractValueType;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.domain.ui.dto.step.ExtractStepDTO;
import com.microsoft.playwright.options.Cookie;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author JingLong
 * @Description 页面信息提取
 * @Date 2025/7/22 14:09
 **/
@Repository
public class PageExtract implements AssociationExtraction {
    @Override
    public Object extract(TestExecutionContext context, ExtractStepDTO extractorInfo) {
        String variableName = null;
        Object extractValue = null;
        Object extractType = extractorInfo.getPageExtractType();
        PageExtractValueType type = PageExtractValueType.valueOf(extractType.toString());
        variableName = extractorInfo.getVariableName();
        Map<String, Object> variables = context.getVariables();

        extractValue = null;
        switch (type) {
            case TITLE -> {
                extractValue = context.getCurrentPage().title();
            }
            case URL -> {
                extractValue = context.getCurrentPage().url();
            }
            case COOKIE -> {
                Cookie cookies = context.getContext().cookies(context.getCurrentPage().url()).get(0);
                Map<String, Object> ck = new HashMap<>();
                ck.put("domain", cookies.domain);
                ck.put("value", cookies.value);
                ck.put("name", cookies.name);
                ck.put("path", cookies.path);
                ck.put("expires", cookies.expires);
                ck.put("httpOnly", cookies.httpOnly);
                ck.put("sameSite", cookies.sameSite);
                ck.put("secure", cookies.secure);
                // 获取cookieName
                if (extractorInfo.getCookieName() != null && !"".equals(extractorInfo.getCookieName())) {
                    if (ck.containsKey(extractorInfo.getCookieName())) {
                        extractValue = ck.get(extractorInfo.getCookieName()).toString();
                    }
                } else {
                    extractValue = ck.toString();
                }
            }
        }
        variables.put(variableName, extractValue);

        // 将抽取的内容存进上下文临时变量表
        context.getVariables().put(variableName, extractValue);

        HashMap<Object, Object> map = new HashMap<>();
        map.put(variableName, extractValue.toString());
        return map;
    }

    @Override
    public boolean isSupport(ExtractType extractType) {
        return ExtractType.PAGE.equals(extractType);
    }
}
