package com.mokatest.platform.demos.element;

import com.mokatest.platform.demos.domain.ui.Element;
import com.mokatest.platform.demos.domain.ui.dto.ElementDTO;
import com.mokatest.platform.demos.mapper.ElementMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * @Author JingLong
 * @Description 元素工具类
 * @Date 2025/8/17 10:47
 **/
@Component
public class ElementProcessor {

    @Resource
    private ElementMapper elementMapper;


    /**
     * 是否存在选择元素
     *
     * @param elementDTO
     * @return
     */
    public boolean isExistSelectElement(ElementDTO elementDTO) {
        Element locator = elementDTO.getLocator();
        if (locator == null) {
            return false;
        }
        return !StringUtils.isEmpty(locator.getLocatorType()) && !StringUtils.isEmpty(locator.getLocatorValue());
    }


    /**
     * 是否存在自定义元素
     *
     * @param elementDTO
     * @return
     */
    public boolean isExistCustomElement(ElementDTO elementDTO) {
        Element customLocator = elementDTO.getCustomLocator();
        if (customLocator == null) {
            return false;
        }
        return !StringUtils.isEmpty(customLocator.getLocatorType()) && !StringUtils.isEmpty(customLocator.getLocatorValue());
    }

    /**
     * 解析步骤实际生效的元素定位。
     * locatorSource=CUSTOM 取自定义，LIBRARY 取库选；为空（历史数据）按「库选优先」规则。
     * 指定来源为空时回退另一侧，两侧都为空时报错。
     */
    public Element getElementLocator(ElementDTO elementDTO) {
        String source = elementDTO.getLocatorSource();
        boolean existSelect = isExistSelectElement(elementDTO);
        boolean existCustom = isExistCustomElement(elementDTO);

        if ("CUSTOM".equalsIgnoreCase(source)) {
            if (existCustom) {
                return elementDTO.getCustomLocator();
            }
            if (existSelect) {
                return resolveSelectElement(elementDTO);
            }
        } else if ("LIBRARY".equalsIgnoreCase(source)) {
            if (existSelect) {
                return resolveSelectElement(elementDTO);
            }
            if (existCustom) {
                return elementDTO.getCustomLocator();
            }
        } else {
            // 历史数据无来源标记：维持「库选优先」规则
            if (existSelect) {
                return resolveSelectElement(elementDTO);
            }
            if (existCustom) {
                return elementDTO.getCustomLocator();
            }
        }
        throw new RuntimeException("元素定位信息缺失");
    }

    /**
     * 库选元素：根据 id 获取库中最新的元素信息，库中不存在则回退步骤内快照
     */
    private Element resolveSelectElement(ElementDTO elementDTO) {
        Integer id = elementDTO.getLocator().getId();
        if (id == null) {
            return elementDTO.getLocator();
        }
        Element element = elementMapper.selectById(id);
        return element == null ? elementDTO.getLocator() : element;
    }
}
