package com.mokatest.platform.demos.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mokatest.platform.demos.domain.ui.Element;
import com.mokatest.platform.demos.domain.ui.dto.queryDto.ElementQueryDTO;
import com.mokatest.platform.demos.domain.ui.vo.ElementVO;
import com.mokatest.platform.demos.domain.ui.vo.ResponseVO;
import com.mokatest.platform.demos.service.ElementService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * UI 自动化元素库管理接口
 *
 * 权限说明：所有接口均基于团队维度 RBAC 进行鉴权，通过 {@link cn.dev33.satoken.annotation.SaCheckPermission} 注解声明。
 *   查看元素：auto:element:view
 *   创建元素：auto:element:create
 *   编辑元素：auto:element:update
 *   删除元素：auto:element:delete
 * admin 角色默认拥有上述所有权限。
 */
@RestController
@RequestMapping("/element")
public class ElementController {

    @Resource
    private ElementService elementService;

    /**
     * 分页获取元素列表
     * 权限：auto:element:view
     */
    @SaCheckPermission("auto:element:view")
    @PostMapping("pageElementList")
    public ResponseVO pageElementList(@RequestBody ElementQueryDTO queryDTO) {
        Page<Element> elementList = elementService.pageElementList(queryDTO);
        return ResponseVO.success(elementList);
    }

    /**
     * 获取元素列表（非分页）
     * 权限：auto:element:view
     */
    @SaCheckPermission("auto:element:view")
    @PostMapping("getElementList")
    public ResponseVO getElementList(@RequestBody ElementQueryDTO queryDTO) {
        List<Element> elementList = elementService.getElementList(queryDTO);
        return ResponseVO.success(elementList);
    }

    /**
     * 获取元素列表（目录列表+元素列表）
     * 权限：auto:element:view
     */
    @SaCheckPermission("auto:element:view")
    @RequestMapping("allElementList")
    public ResponseVO allElementList(@RequestParam String projectId) {
        List<Element> elementList = elementService.allElementList(projectId);
        return ResponseVO.success(elementList);
    }

    /**
     * 获取目录列表（只获取目录）
     * 权限：auto:element:view
     */
    @SaCheckPermission("auto:element:view")
    @RequestMapping("folderList")
    public ResponseVO folderList(@RequestParam(name = "projectId") String projectId) {
        List<ElementVO> elementList = elementService.folderList(projectId);
        return ResponseVO.success(elementList);
    }

    /**
     * 获取指定目录下的元素列表
     * 权限：auto:element:view
     */
    @SaCheckPermission("auto:element:view")
    @PostMapping("getElementListByFolderId")
    public ResponseVO getElementListByFolderId(@RequestBody Map<String, String> param) {
        List<Element> elementList = elementService.getElementListByFolderId(param);
        return ResponseVO.success(elementList);
    }

    /**
     * 添加目录 or 元素
     * 权限：auto:element:create
     */
    @SaCheckPermission("auto:element:create")
    @PostMapping("add")
    public ResponseVO addFolderOrElement(@RequestBody Element element) {
        Boolean result = elementService.addFolderOrElement(element);
        return ResponseVO.success(result);
    }

    /**
     * 更新目录 or 元素
     * 权限：auto:element:update
     */
    @SaCheckPermission("auto:element:update")
    @PostMapping("update")
    public ResponseVO updateFolderOrElement(@RequestBody Element element) {
        Boolean result = elementService.updateFolderOrElement(element);
        return ResponseVO.success(result);
    }

    /**
     * 根据id 删除目录 or 元素
     * 权限：auto:element:delete
     */
    @SaCheckPermission("auto:element:delete")
    @GetMapping("deleteElementOrFolder")
    public ResponseVO deleteElementOrFolder(@RequestParam Integer elementId) {
        Boolean result = elementService.deleteElementOrFolder(elementId);
        return ResponseVO.success(result);
    }

    /**
     * 根据id列表删除元素
     * 权限：auto:element:delete
     */
    @SaCheckPermission("auto:element:delete")
    @PostMapping("deleteElementBatch")
    public ResponseVO deleteElementBatch(@RequestBody List<Integer> elementIds) {
        Boolean result = elementService.deleteElementBatch(elementIds);
        return ResponseVO.success(result);
    }

    /**
     * 根据元素id获取元素
     * 权限：auto:element:view
     */
    @SaCheckPermission("auto:element:view")
    @GetMapping("getElementById")
    public ResponseVO getElementById(@RequestParam Integer elementId) {
        Element result = elementService.getElementById(elementId);
        return ResponseVO.success(result);
    }

    /**
     * 更新元素/目录排序
     * 权限：auto:element:update
     */
    @SaCheckPermission("auto:element:update")
    @PostMapping("updateElementSort")
    public ResponseVO updateElementSort(@RequestBody List<ElementVO> elementVOList) {
        Boolean result = elementService.updateElementSort(elementVOList);
        return ResponseVO.success(result);
    }
}
