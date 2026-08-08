package com.mokatest.platform.demos.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.domain.ui.uiEnum.element.ElementLocatorType;
import com.mokatest.platform.demos.domain.ui.uiEnum.element.ElementType;
import com.mokatest.platform.demos.domain.ui.Element;
import com.mokatest.platform.demos.domain.ui.dto.queryDto.ElementQueryDTO;
import com.mokatest.platform.demos.domain.ui.vo.ElementVO;
import com.mokatest.platform.demos.exception.ParamIsEmptyException;
import com.mokatest.platform.demos.mapper.ElementMapper;
import com.mokatest.platform.demos.service.ElementService;
import jakarta.annotation.Resource;
import org.apache.ibatis.executor.BatchResult;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.mokatest.platform.demos.domain.ui.uiEnum.element.ElementType.ELEMENT;
import static com.mokatest.platform.demos.domain.ui.uiEnum.element.ElementType.FOLDER;

/**
 * @author: JingLong
 * @description 针对表【element(元素定位表)】的数据库操作Service实现
 * @createDate 2025-08-02 15:06:46
 */
@Service
public class ElementServiceImpl extends ServiceImpl<ElementMapper, Element> implements ElementService {


    @Resource
    private ElementMapper elementMapper;


    @Override
    public List<Element> allElementList(String projectId) {
        if (projectId == null || "".equals(projectId)) {
            throw new ParamIsEmptyException("缺少项目Id");
        }
        QueryWrapper<Element> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("project_id", projectId);
        queryWrapper.eq("element_type", ELEMENT);
        return elementMapper.selectList(queryWrapper);
    }

    @Override
    public List<ElementVO> folderList(String projectId) {
        if (projectId == null || "".equals(projectId)) {
            throw new ParamIsEmptyException("缺少项目Id");
        }
        QueryWrapper<Element> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("project_id", projectId);
        queryWrapper.eq("element_type", FOLDER);
        List<Element> elementList = elementMapper.selectList(queryWrapper);
        // 找出所有顶层节点（不包括ELEMENT）
        List<Element> parentElement = elementList.stream().filter(element -> {
            return element.getParentId() != null && 0 == element.getParentId();
        }).sorted(Comparator.comparing(Element::getSort)).toList();
        List<ElementVO> result = new ArrayList<>();
        for (Element element : parentElement) {
            ElementVO elementVO = new ElementVO();
            elementVO.setChildren(new ArrayList<>());
            BeanUtils.copyProperties(element, elementVO);
            buildFolderTree(elementVO, elementList);
            result.add(elementVO);
        }
        // 必须给一个id是0的根目录（虚拟的）。作用：初始化没有任何目录的时候，添加目录是需要父目录的
        ElementVO rootFolder = new ElementVO();
        rootFolder.setId(0);
        rootFolder.setElementName("根目录");
        rootFolder.setElementType(FOLDER);
        rootFolder.setSort(0);
        rootFolder.setChildren(result);
        List<ElementVO> elementVOS = new ArrayList<>();
        elementVOS.add(rootFolder);
        return elementVOS;
    }

    @Override
    public List<Element> getElementListByFolderId(Map<String, String> param) {
        if (param == null || param.get("folderId") == null || param.get("projectId") == null) {
            throw new ParamIsEmptyException("缺少必要参数");
        }
        QueryWrapper<Element> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("parent_id", param.get("folderId"));
        queryWrapper.eq("project_id", param.get("projectId"));
        queryWrapper.eq("element_type", ELEMENT);
        return elementMapper.selectList(queryWrapper);
    }

    @Override
    public Boolean addFolderOrElement(Element element) {
        if (element == null) {
            throw new ParamIsEmptyException("缺少参数");
        }
        ElementType elementType = ElementType.valueOf((String) element.getElementType());
        // 判断类型
        if (FOLDER.equals(elementType)) {
            // 如果是目录，需要对其进行排序
            Integer parentId = element.getParentId();
            QueryWrapper<Element> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("parent_id", parentId);
            queryWrapper.eq("element_type", FOLDER);
            queryWrapper.eq("project_id", element.getProjectId());
            Long childCount = elementMapper.selectCount(queryWrapper);
            element.setSort(Integer.valueOf(childCount.toString()) + 1);
        }
        String loginId = StpUtil.getLoginIdAsString();
        element.setCreateUserId(loginId);
        element.setUpdateUserId(loginId);
        int insert = elementMapper.insert(element);
        return insert > 0;
    }

    @Override
    public Boolean updateFolderOrElement(Element element) {
        if (element == null) {
            throw new ParamIsEmptyException("缺少参数");
        }
        element.setUpdateUserId(StpUtil.getLoginIdAsString());
        int update = elementMapper.updateById(element);
        return update > 0;
    }

    @Override
    @Transactional
    public Boolean deleteElementOrFolder(Integer elementId) {
        if (elementId == null) {
            throw new ParamIsEmptyException("缺少参数");
        }
        // 查询对应元素
        Element element = elementMapper.selectById(elementId);
        if (element == null) {
            return true;
        }
        Date now = new Date();
        // 逻辑删除元素本身
        element.setDeletedAt(now);
        int i = elementMapper.deleteById(element);
        if (i <= 0) {
            throw new RuntimeException("删除失败");
        }

        ElementType elementType = ElementType.valueOf((String) element.getElementType());
        if (FOLDER.equals(elementType)) {
            // 只有目录才对其进行重排序，且只对目录进行重排
            // 对删除元素父目录下的所有元素进行排序（@TableLogic 已自动过滤已删除记录）
            QueryWrapper<Element> sceneQueryWrapper = new QueryWrapper<>();
            sceneQueryWrapper.eq("parent_id", element.getParentId());
            sceneQueryWrapper.eq("project_id", element.getProjectId());
            sceneQueryWrapper.eq("element_type", FOLDER);
            List<Element> updateElementList = elementMapper.selectList(sceneQueryWrapper);
            if (!updateElementList.isEmpty()) {
                Integer sort = element.getSort();
                // 排序
                List<Element> result =
                        updateElementList.stream().filter(element1 -> element1.getSort() > sort).toList();
                if (!result.isEmpty()) {
                    // 将被删除元素的后续元素排序sort都-1
                    for (Element element1 : result) {
                        element1.setSort(element1.getSort() - 1);
                    }
                    // 批量更新
                    List<BatchResult> batchResults = elementMapper.insertOrUpdate(result);
                }
            }

            // 目录需要递归逻辑删除其子目录和元素
            QueryWrapper<Element> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("parent_id", elementId);
            queryWrapper.eq("project_id", element.getProjectId());
            List<Element> elementList = elementMapper.selectList(queryWrapper);
            if (elementList.isEmpty()) {
                return true;
            }
            // 逻辑删除子目录和元素
            for (Element childElement : elementList) {
                childElement.setDeletedAt(now);
                elementMapper.deleteById(childElement);
                // 递归删除子目录、元素
                deleteElementRecursive(childElement, now);
            }
        }
        return true;
    }


    @Override
    @Transactional
    public Boolean deleteElementBatch(List<Integer> elementIds) {
        if (elementIds == null || elementIds.isEmpty()) {
            return true;
        }
        List<Element> elements = elementMapper.selectBatchIds(elementIds);
        if (elements == null || elements.isEmpty()) {
            return true;
        }
        Date now = new Date();
        Set<Integer> parentIdSet = new HashSet<>();
        String projectId = null;
        for (Element element : elements) {
            if (element == null) {
                continue;
            }
            element.setDeletedAt(now);
            elementMapper.deleteById(element);
            if (element.getParentId() != null) {
                parentIdSet.add(element.getParentId());
            }
            if (projectId == null && element.getProjectId() != null) {
                projectId = element.getProjectId();
            }
        }
        // 批量删除后，对每个受影响的父目录下的元素重新排序
        if (projectId != null && !parentIdSet.isEmpty()) {
            for (Integer parentId : parentIdSet) {
                if (parentId == null) {
                    continue;
                }
                QueryWrapper<Element> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("parent_id", parentId);
                queryWrapper.eq("project_id", projectId);
                queryWrapper.eq("element_type", ELEMENT);
                queryWrapper.orderByAsc("sort");
                List<Element> siblings = elementMapper.selectList(queryWrapper);
                if (siblings == null || siblings.isEmpty()) {
                    continue;
                }
                for (int i = 0; i < siblings.size(); i++) {
                    siblings.get(i).setSort(i + 1);
                }
                elementMapper.insertOrUpdate(siblings);
            }
        }
        return true;
    }

    @Override
    public Page<Element> pageElementList(ElementQueryDTO queryDTO)  {
        if (queryDTO == null) {
            throw new ParamIsEmptyException("缺少参数");
        }
        // 创建分页对象
        Page<Element> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());

        // 创建查询条件
        LambdaQueryWrapper<Element> queryWrapper = new LambdaQueryWrapper<>();

        // 添加查询条件
        if (StringUtils.isNotBlank(queryDTO.getElementName())) {
            queryWrapper.like(Element::getElementName, queryDTO.getElementName());
        }

        if (queryDTO.getParentId() != null) {
            queryWrapper.eq(Element::getParentId, queryDTO.getParentId());
        }

        if (queryDTO.getElementType() != null) {
            queryWrapper.eq(Element::getElementType, ElementType.valueOf(queryDTO.getElementType().toString()));
        }

        if (StringUtils.isNotBlank(queryDTO.getLocatorType())) {
            queryWrapper.eq(Element::getLocatorType, ElementLocatorType.valueOf(queryDTO.getLocatorType().toString()));
        }

        if (StringUtils.isNotBlank(queryDTO.getProjectId())) {
            queryWrapper.eq(Element::getProjectId, queryDTO.getProjectId());
        }

        // todo 后期根据情况处理
      /*  if (queryDTO.getIsShared() != null) {
            queryWrapper.eq(Element::getIsShared, queryDTO.getIsShared());
        }*/

        // 添加排序
        queryWrapper.orderByDesc(Element::getCreatedAt);

        // 执行分页查询
        return baseMapper.selectPage(page, queryWrapper);
    }

    @Override
    public List<Element> getElementList(ElementQueryDTO queryDTO) {
        if (queryDTO == null) {
            throw new ParamIsEmptyException("缺少参数");
        }
        LambdaQueryWrapper<Element> queryWrapper = new LambdaQueryWrapper<>();
        // 添加查询条件
        if (StringUtils.isNotBlank(queryDTO.getElementName())) {
            queryWrapper.like(Element::getElementName, queryDTO.getElementName());
        }

        if (queryDTO.getParentId() != null) {
            queryWrapper.eq(Element::getParentId, queryDTO.getParentId());
        }

        if (queryDTO.getElementType() != null) {
            queryWrapper.eq(Element::getElementType, ElementType.valueOf(queryDTO.getElementType().toString()));
        }

        if (StringUtils.isNotBlank(queryDTO.getLocatorType())) {
            queryWrapper.eq(Element::getLocatorType, ElementLocatorType.valueOf(queryDTO.getLocatorType().toString()));
        }

        if (StringUtils.isNotBlank(queryDTO.getProjectId())) {
            queryWrapper.eq(Element::getProjectId, queryDTO.getProjectId());
        }
        return elementMapper.selectList(queryWrapper);
    }

    @Override
    public Element getElementById(Integer elementId) {
        if (elementId == null) {
            throw new ParamIsEmptyException("缺少参数");
        }
        return elementMapper.selectById(elementId);
    }

    @Override
    public Boolean updateElementSort(List<ElementVO> elementVOList) {
        if (elementVOList == null || elementVOList.isEmpty()) return true;
        // 将数据打平
        List<Element> flatElements = new ArrayList<>();
        for (ElementVO elementVO : elementVOList) {
            Element element = new Element();
            BeanUtils.copyProperties(elementVO, element);
            flatElements.add(element);
            flatElementTree(elementVO, flatElements);
        }
        // 批量保存
        if (flatElements.isEmpty()) return true;
        List<BatchResult> batchResults = elementMapper.updateById(flatElements);
        boolean allSuccess = batchResults.stream().allMatch(result -> result.getUpdateCounts().length > 0);
        return allSuccess;
    }

    private void flatElementTree(ElementVO parentVO, List<Element> result) {
        if (parentVO.getChildren() != null && !parentVO.getChildren().isEmpty()) {
            // 递归添加
            for (ElementVO elementVO : parentVO.getChildren()) {
                Element element = new Element();
                BeanUtils.copyProperties(elementVO, element);
                result.add(element);
                flatElementTree(elementVO, result);
            }
        }

    }


    /**
     * 构建目录树
     *
     * @param parentVO
     * @param elementList
     */
    private void buildFolderTree(ElementVO parentVO, List<Element> elementList) {
        // 如果是元素一定是没有子节点的
        if (ELEMENT.equals(parentVO.getElementType())) return;

        Integer parentId = parentVO.getId();
        // 找子节点
        List<Element> children = elementList.stream().filter(element -> {
            return Objects.equals(parentId, element.getParentId());
        }).sorted(Comparator.comparing(Element::getSort)).toList();
        if (children.isEmpty()) return;
        for (Element element : children) {
            ElementVO elementVO = new ElementVO();
            elementVO.setChildren(new ArrayList<>());
            BeanUtils.copyProperties(element, elementVO);
            buildFolderTree(elementVO, elementList);
            parentVO.getChildren().add(elementVO);
        }
    }

    private void deleteElementRecursive(Element parentElement, Date deletedAt) {
        QueryWrapper<Element> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("parent_id", parentElement.getId());
        queryWrapper.eq("project_id", parentElement.getProjectId());
        List<Element> elementList = elementMapper.selectList(queryWrapper);
        if (elementList.isEmpty()) return;
        for (Element element : elementList) {
            // 逻辑删除子节点
            element.setDeletedAt(deletedAt);
            elementMapper.deleteById(element);
            // 判断，如果是目录结构，就需要继续递归
            if (FOLDER.equals(ElementType.valueOf(element.getElementType().toString()))) {
                deleteElementRecursive(element, deletedAt);
            }
        }
    }
}




