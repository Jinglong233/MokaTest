package com.mokatest.platform.demos.domain.ui.uiEnum.element;

import com.microsoft.playwright.options.AriaRole;

/**
 * @Author JingLong
 * @Description 元素定位角色类型
 * @Date 2025/8/31 10:17
 **/
public enum ElementLocatorRoleType {
    BUTTON, TEXTBOX, LINK, CHECKBOX, RADIO, HEADING, OPTION, MENUITEM;

    /**
     * 将字符串转换为 AriaRole
     */
    public static AriaRole parseRoleType(String roleType) {
        switch (roleType.toLowerCase()) {
            case "button": return AriaRole.BUTTON;
            case "textbox": return AriaRole.TEXTBOX;
            case "link": return AriaRole.LINK;
            case "checkbox": return AriaRole.CHECKBOX;
            case "radio": return AriaRole.RADIO;
            case "heading": return AriaRole.HEADING;
            case "combobox": return AriaRole.COMBOBOX;
            case "searchbox": return AriaRole.SEARCHBOX;
            case "menuitem": return AriaRole.MENUITEM;
            case "option": return AriaRole.OPTION;
            case "img": return AriaRole.IMG;
            case "listitem": return AriaRole.LISTITEM;
            case "table": return AriaRole.TABLE;
            case "row": return AriaRole.ROW;
            case "cell": return AriaRole.CELL;
            case "dialog": return AriaRole.DIALOG;
            case "alert": return AriaRole.ALERT;
            case "progressbar": return AriaRole.PROGRESSBAR;
            case "slider": return AriaRole.SLIDER;
            default: throw new IllegalArgumentException("不支持的 role 类型: " + roleType);
        }
    }
}
