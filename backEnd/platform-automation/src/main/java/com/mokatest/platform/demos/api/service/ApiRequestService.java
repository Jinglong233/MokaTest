package com.mokatest.platform.demos.api.service;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.api.domain.ApiRequest;
import com.mokatest.platform.demos.api.domain.dto.AddApiInterfaceDTO;
import com.mokatest.platform.demos.api.domain.vo.ApiFolderTreeVO;

import java.util.List;

/**
* @author: JingLong
* @description 针对表【api_request(接口表)】的数据库操作Service
* @createDate 2026-04-03 11:16:56
*/
public interface ApiRequestService extends IService<ApiRequest> {

    SaResult saveOrUpdate(AddApiInterfaceDTO addApiInterfaceDTO);

    SaResult folderList(Integer projectId);

    SaResult apiListTree(Integer projectId);

    SaResult deleteApi(Integer id);

    SaResult updateApiSort(List<ApiFolderTreeVO> apiRequestVOS);

    SaResult copyApi(Integer id);

    SaResult debug(Integer id);

    SaResult getApiById(Integer id);

    /**
     * 保存接口用例
     * @param addApiInterfaceDTO 接口数据
     * @return 保存结果
     */
    SaResult saveCase(AddApiInterfaceDTO addApiInterfaceDTO);

    /**
     * 查询接口下的用例列表
     * @param sourceId 来源接口ID
     * @return 用例列表
     */
    SaResult getCases(Integer sourceId);

    /**
     * 获取接口-用例关系树
     * 结构：接口（父节点）-> 用例（子节点）
     * 只能选择用例，不能选择接口
     * @param projectId 项目ID
     * @return 接口-用例树
     */
    SaResult interfaceCaseTree(Integer projectId);

    /**
     * 根据API配置直接调试（不查询数据库）
     * 用于场景中API步骤的副本模式调试
     * @param apiRequest API配置对象
     * @return 调试结果
     */
    SaResult debugByConfig(ApiRequest apiRequest);

    /**
     * 导入 Swagger / OpenAPI 文档
     * @param dto 导入参数
     * @param file 上传的文件（与 dto.url 二选一）
     * @return 导入结果
     */
    SaResult importSwagger(com.mokatest.platform.demos.api.domain.dto.SwaggerImportDTO dto,
                           org.springframework.web.multipart.MultipartFile file);
}
