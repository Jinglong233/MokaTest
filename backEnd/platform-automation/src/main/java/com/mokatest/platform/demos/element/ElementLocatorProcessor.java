package com.mokatest.platform.demos.element;

import com.mokatest.platform.demos.domain.ui.uiEnum.element.ElementLocatorRoleType;
import com.mokatest.platform.demos.domain.ui.uiEnum.element.ElementLocatorType;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;

/**
 * @Author JingLong
 * @Description 元素定位器构建器
 * @Date 2025/7/26 11:15
 **/
public class ElementLocatorProcessor {

    /**
     * 只对定位表达式做些简单的处理
     *
     * @param locatorType
     * @param locatorValue
     * @return
     */
    public static Locator process(Frame frame, ElementLocatorType locatorType, String locatorValue) {
        Locator result = null;
        switch (locatorType) {
            case XPATH -> {
                result = frame.locator(String.format("xpath=%s", locatorValue));
            }
            case TEXT -> {
                result = frame.getByText(locatorValue);
            }
            case PLACEHOLDER -> {
                result = frame.getByPlaceholder(locatorValue);
            }
            case ROLE -> {
                // 如果是role，解析参数
                String[] strs = parseRoleString(locatorValue);
                AriaRole ariaRole = ElementLocatorRoleType.parseRoleType(strs[0]);
                result = frame.getByRole(ariaRole, new Frame.GetByRoleOptions().setName(strs[1]).setExact(true));
            }
            case TEST_ID -> {
                result = frame.getByTestId(locatorValue);
            }
            case LABEL -> {
                result = frame.getByLabel(locatorValue);
            }
            case TITLE -> {
                result = frame.getByTitle(locatorValue);
            }
            case ALT -> {
                result = frame.getByAltText(locatorValue);
            }
            case CSS ->{
                result = frame.locator(locatorValue);
            }
            default -> throw new RuntimeException("不支持的定位类型");
        }
        return result;
    }

    private static String[] parseRoleString(String roleString) {
        // 只分割第一个 ::
        int firstColonIndex = roleString.indexOf("::");
        if (firstColonIndex == -1) {
            // 如果没有 ::，只有角色类型
            return new String[]{roleString.trim(), ""};
        }

        // 分割成两部分
        String roleType = roleString.substring(0, firstColonIndex).trim();
        String name = roleString.substring(firstColonIndex + 2).trim(); // +2 跳过 ::

        return new String[]{roleType, name};
    }


}
