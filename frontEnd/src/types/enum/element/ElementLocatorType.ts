// 元素定位类型
export enum ElementLocatorType {

    XPATH = "XPATH",
    TEXT = "TEXT",
    TEXT_CONTAINS = "TEXT_CONTAINS",
    PLACEHOLDER = "PLACEHOLDER",
    ROLE = "ROLE",
    TEST_ID = "TEST_ID",
    LABEL = "LABEL",
    TITLE = "TITLE",
    ALT = "ALT",
    CSS = "CSS"
}

// 定位类型下拉选项（label 走 i18n：element.locatorType.*）
export const LOCATOR_TYPE_OPTIONS: { value: ElementLocatorType; labelKey: string }[] = [
    {value: ElementLocatorType.XPATH, labelKey: 'element.locatorType.xpath'},
    {value: ElementLocatorType.TEXT, labelKey: 'element.locatorType.text'},
    {value: ElementLocatorType.TEXT_CONTAINS, labelKey: 'element.locatorType.textContains'},
    {value: ElementLocatorType.PLACEHOLDER, labelKey: 'element.locatorType.placeholder'},
    {value: ElementLocatorType.ROLE, labelKey: 'element.locatorType.role'},
    {value: ElementLocatorType.TEST_ID, labelKey: 'element.locatorType.testId'},
    {value: ElementLocatorType.LABEL, labelKey: 'element.locatorType.label'},
    {value: ElementLocatorType.TITLE, labelKey: 'element.locatorType.title'},
    {value: ElementLocatorType.ALT, labelKey: 'element.locatorType.alt'},
    {value: ElementLocatorType.CSS, labelKey: 'element.locatorType.css'},
];
