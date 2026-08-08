import {Element} from "@/types/domain/Element";

export class ElementDTO {
    // 选择元素的
    locator: Element = new Element();
    // 自定义元素定位的
    customLocator: Element = new Element();
    // 定位来源：LIBRARY=库选元素，CUSTOM=自定义定位；空表示历史数据，后端按「库选优先」规则取值
    locatorSource: string = '';
}


