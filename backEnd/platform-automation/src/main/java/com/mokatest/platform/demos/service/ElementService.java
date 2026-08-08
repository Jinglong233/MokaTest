package com.mokatest.platform.demos.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.domain.ui.Element;
import com.mokatest.platform.demos.domain.ui.dto.queryDto.ElementQueryDTO;
import com.mokatest.platform.demos.domain.ui.vo.ElementVO;

import java.util.List;
import java.util.Map;

/**
 * @author: JingLong
 * @description 针对表【element(元素定位表)】的数据库操作Service
 * @createDate 2025-08-02 15:06:46
 */
public interface ElementService extends IService<Element> {

    List<Element> allElementList(String projectId);

    List<ElementVO> folderList(String projectId);

    List<Element> getElementListByFolderId(Map<String, String> param);


    Boolean addFolderOrElement(Element element);

    Boolean updateFolderOrElement(Element element);

    Boolean deleteElementOrFolder(Integer elementId);


    Boolean deleteElementBatch(List<Integer> elementIds);

    Page<Element> pageElementList(ElementQueryDTO queryDTO);

    List<Element> getElementList(ElementQueryDTO queryDTO);

    Element getElementById(Integer elementId);

    Boolean updateElementSort(List<ElementVO> elementVOList);
}
