package com.mokatest.platform.demos.api.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.api.domain.ApiRequest;
import com.mokatest.platform.demos.api.domain.dto.AddApiInterfaceDTO;
import com.mokatest.platform.demos.api.domain.dto.SwaggerImportDTO;
import com.mokatest.platform.demos.api.domain.vo.ApiFolderTreeVO;
import com.mokatest.platform.demos.api.service.ApiRequestService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;

/**
 * API 自动化接口管理接口
 *
 * 权限说明：所有接口均基于团队维度 RBAC 进行鉴权，通过 {@link cn.dev33.satoken.annotation.SaCheckPermission} 注解声明。
 *   查看接口/目录/用例树：auto:api:view
 *   保存接口/用例：auto:api:update（覆盖新增与编辑）
 *   删除接口：auto:api:delete
 *   复制接口：auto:api:create
 *   调试接口：auto:api:execute
 * admin 角色默认拥有上述所有权限。
 */
@RestController
@RequestMapping("/interface")
public class APIController {

    @Resource
    private ApiRequestService apiRequestService;

    /**
     * 保存接口（新增/编辑统一入口）
     * 权限：auto:api:update
     */
    @SaCheckPermission("auto:api:update")
    @RequestMapping("/save")
    public SaResult save(@RequestBody AddApiInterfaceDTO addApiInterfaceDTO) {
        return apiRequestService.saveOrUpdate(addApiInterfaceDTO);
    }

    /**
     * 获取目录列表
     * 权限：auto:api:view
     */
    @SaCheckPermission("auto:api:view")
    @RequestMapping("/folderList")
    public SaResult folderList(@RequestParam Integer projectId) {
        return apiRequestService.folderList(projectId);
    }

    /**
     * 获取api接口的列表树
     * 权限：auto:api:view
     */
    @SaCheckPermission("auto:api:view")
    @RequestMapping("/apiListTree")
    public SaResult apiListTree(@RequestParam Integer projectId) {
        return apiRequestService.apiListTree(projectId);
    }

    /**
     * 删除接口
     * 权限：auto:api:delete
     */
    @SaCheckPermission("auto:api:delete")
    @RequestMapping("/deleteApi")
    public SaResult deleteApi(@RequestParam Integer id) {
        return apiRequestService.deleteApi(id);
    }

    /**
     * 更新接口列表排序
     * 权限：auto:api:update
     */
    @SaCheckPermission("auto:api:update")
    @RequestMapping("/updateApiSort")
    public SaResult updateApiSort(@RequestBody List<ApiFolderTreeVO> apiRequestVOS) {
        return apiRequestService.updateApiSort(apiRequestVOS);
    }

    /**
     * 根据id获取接口详情
     * 权限：auto:api:view
     */
    @SaCheckPermission("auto:api:view")
    @RequestMapping("/getApiById")
    public SaResult getApiById(@RequestParam Integer id) {
        return apiRequestService.getApiById(id);
    }

    /**
     * 复制接口
     * 权限：auto:api:create
     */
    @SaCheckPermission("auto:api:create")
    @RequestMapping("/copyApi")
    public SaResult copyApi(@RequestParam Integer id) {
        return apiRequestService.copyApi(id);
    }

    /**
     * 导入 Swagger / OpenAPI 文档
     * 权限：auto:api:create
     */
    @SaCheckPermission("auto:api:create")
    @RequestMapping("/importSwagger")
    public SaResult importSwagger(SwaggerImportDTO dto,
                                  @RequestParam(required = false) MultipartFile file) {
        if (StringUtils.isNotBlank(dto.getUrl())) {
            String checkResult = checkUrlSafety(dto.getUrl());
            if (checkResult != null) {
                return SaResult.error("URL 校验失败：" + checkResult);
            }
        }
        return apiRequestService.importSwagger(dto, file);
    }

    /**
     * 调试接口
     * 权限：auto:api:execute
     */
    @SaCheckPermission("auto:api:execute")
    @RequestMapping("/debug")
    public SaResult debug(@RequestParam Integer id) {
        return apiRequestService.debug(id);
    }

    /**
     * 保存接口用例
     * 权限：auto:api:update
     */
    @SaCheckPermission("auto:api:update")
    @RequestMapping("/saveCase")
    public SaResult saveCase(@RequestBody AddApiInterfaceDTO addApiInterfaceDTO) {
        return apiRequestService.saveCase(addApiInterfaceDTO);
    }

    /**
     * 查询接口下的用例列表
     * 权限：auto:api:view
     */
    @SaCheckPermission("auto:api:view")
    @RequestMapping("/cases")
    public SaResult getCases(@RequestParam Integer sourceId) {
        return apiRequestService.getCases(sourceId);
    }

    /**
     * 获取接口-用例关系树
     * 权限：auto:api:view
     */
    @SaCheckPermission("auto:api:view")
    @RequestMapping("/interfaceCaseTree")
    public SaResult interfaceCaseTree(@RequestParam Integer projectId) {
        return apiRequestService.interfaceCaseTree(projectId);
    }

    /**
     * 根据API配置直接调试（不查询数据库）
     * 权限：auto:api:execute
     */
    @SaCheckPermission("auto:api:execute")
    @RequestMapping("/debugByConfig")
    public SaResult debugByConfig(@RequestBody ApiRequest apiRequest) {
        // URL 安全检查（校验拼接后的最终 URL：相对路径 + 环境服务地址 baseUrl 是合法组合；
        // 路径含变量占位时无法静态求值，跳过校验交给执行期）
        if (apiRequest != null && apiRequest.getRequestPath() != null
                && !containsVariablePlaceholder(apiRequest.getRequestPath())) {
            String checkResult = checkUrlSafety(resolveEffectiveUrl(apiRequest));
            if (checkResult != null) {
                return SaResult.error(checkResult);
            }
        }
        return apiRequestService.debugByConfig(apiRequest);
    }

    /**
     * 解析最终请求 URL：绝对路径原样返回（优先于 baseUrl，与执行器 buildUrl 口径一致）；
     * 相对路径拼接 envInfo.baseUrl（服务下拉选择后写入）。
     * 无 baseUrl 的相对路径原样返回，由安全校验按原逻辑拦截。
     */
    private String resolveEffectiveUrl(ApiRequest apiRequest) {
        String path = apiRequest.getRequestPath().trim();
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        String baseUrl = apiRequest.getEnvInfo() != null ? apiRequest.getEnvInfo().getBaseUrl() : null;
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return path;
        }
        String base = baseUrl.trim();
        if (base.endsWith("/") && path.startsWith("/")) {
            return base + path.substring(1);
        }
        if (!base.endsWith("/") && !path.startsWith("/")) {
            return base + "/" + path;
        }
        return base + path;
    }

    /**
     * 路径含变量占位（${var} / {{var}}）：此处无法求值，跳过静态校验，
     * 最终 URL 由执行器变量替换后自行解析（执行器对非法地址会报执行期错误）
     */
    private boolean containsVariablePlaceholder(String text) {
        return text != null && (text.contains("${") || text.contains("{{"));
    }

    /**
     * URL 安全检查：禁止访问内网地址和非 http(s) 协议
     * @return null 表示安全；否则返回错误信息
     */
    private String checkUrlSafety(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "URL 不能为空";
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return "只允许 http:// 或 https:// 协议";
            }
            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                return "URL 格式不正确，无法解析主机名";
            }
            // 注：作为测试平台，被测系统常部署在内网/本地，不拦截 localhost 与内网地址
        } catch (Exception e) {
            return "URL 格式不正确：" + e.getMessage();
        }
        return null;
    }
}
