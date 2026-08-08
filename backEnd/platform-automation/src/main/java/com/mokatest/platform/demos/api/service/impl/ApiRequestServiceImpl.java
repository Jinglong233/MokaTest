package com.mokatest.platform.demos.api.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import cn.hutool.core.bean.BeanUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.api.domain.ApiRequest;
import com.mokatest.platform.demos.api.domain.GlobalVar;
import com.mokatest.platform.demos.api.domain.apiEnum.ApiNodeType;
import com.mokatest.platform.demos.api.domain.apiEnum.ApiType;
import com.mokatest.platform.demos.api.domain.apiEnum.BodyMode;
import com.mokatest.platform.demos.api.domain.apiEnum.GlobalRequestVarType;
import com.mokatest.platform.demos.api.domain.apiEnum.ParameterType;
import com.mokatest.platform.demos.api.domain.apiEnum.RequestMethod;
import com.mokatest.platform.demos.api.domain.apiEnum.RuleSource;
import com.mokatest.platform.demos.api.domain.dto.AddApiInterfaceDTO;
import com.mokatest.platform.demos.api.domain.dto.SwaggerImportDTO;
import com.mokatest.platform.demos.api.domain.requestModel.Body;
import com.mokatest.platform.demos.api.domain.requestModel.MockResponse;
import com.mokatest.platform.demos.api.domain.requestModel.RequestExecuteInfo;
import com.mokatest.platform.demos.api.domain.requestModel.RequestParameter;
import com.mokatest.platform.demos.api.domain.vo.ApiFolderTreeVO;
import com.mokatest.platform.demos.api.domain.requestModel.ApiExtraction;
import com.mokatest.platform.demos.api.domain.requestModel.ApiResponseExample;
import com.mokatest.platform.demos.api.domain.requestModel.AssertParameter;
import com.mokatest.platform.demos.api.domain.vo.SwaggerImportResultVO;
import com.mokatest.platform.demos.api.http.assertion.ApiAssertExecutor;
import com.mokatest.platform.demos.api.http.executor.RequestExecutor;
import com.mokatest.platform.demos.api.http.executor.RequestExecutorFactory;
import com.mokatest.platform.demos.api.http.executor.impl.SqlRequestExecutor;
import com.mokatest.platform.demos.api.http.extraction.ExtractionDetail;
import com.mokatest.platform.demos.api.http.extraction.ExtractionExecutor;
import com.mokatest.platform.demos.api.http.model.TestHttpResponse;
import com.mokatest.platform.demos.api.http.validation.SchemaValidator;
import com.mokatest.platform.demos.result.AssertResult;
import com.mokatest.platform.demos.api.mapper.ApiRequestMapper;
import com.mokatest.platform.demos.api.mapper.GlobalVarMapper;
import com.mokatest.platform.demos.api.service.ApiRequestService;
import com.mokatest.platform.demos.listener.projectListener.Enum.UpdateDataType;
import com.mokatest.platform.demos.listener.projectListener.ProjectUpdateEvent;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.executor.BatchResult;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * API 请求业务服务实现类
 *
 * 功能说明：提供 API 接口的增删改查、文件夹管理、复制、排序、调试执行等核心业务逻辑
 *
 * 核心职责：
 *   - 接口管理：保存/更新、删除、复制、排序 API 接口和文件夹
 *   - 树形结构：构建 API 文件夹与接口的树形目录结构
 *   - 调试执行：根据接口 ID 执行 HTTP 请求，支持变量替换和数据提取
 *
 * 调试执行流程：
 *   - 根据接口 ID 查询接口配置（包含请求方法、URL、Header、Body 等）
 *   - 通过 RequestExecutorFactory 获取对应请求方法的执行器
 *   - 调用 RequestExecutor 执行 HTTP 请求
 *   - 执行完成后，调用 ExtractionExecutor 进行数据提取（如提取 token 等）
 *   - 将提取结果注入响应对象返回给前端
 *
 * 依赖注入：
 *   - RequestExecutorFactory - 根据请求方法（GET/POST 等）创建对应的执行器
 *   - ExtractionExecutor - 从响应中提取数据到变量池
 *   - ApiRequestMapper - 数据库访问层
 *
 * @author JingLong
 * @see ApiRequestService
 * @since 2026-04-03
 */
@Service
public class ApiRequestServiceImpl extends ServiceImpl<ApiRequestMapper, ApiRequest>
        implements ApiRequestService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApiRequestServiceImpl.class);

    /**
     * 请求执行器工厂，根据请求方法（GET/POST/PUT/DELETE 等）创建对应的执行器实例
     */
    private final RequestExecutorFactory executorFactory;

    /**
     * 数据提取执行器，在 HTTP 请求完成后从响应中提取数据到变量池
     */
    private final ExtractionExecutor extractionExecutor;

    /**
     * 断言执行器，在 HTTP 请求完成后对响应结果进行断言验证
     */
    private final ApiAssertExecutor assertExecutor;

    /**
     * SQL 请求执行器（apiType=SQL 时使用）
     */
    private final SqlRequestExecutor sqlRequestExecutor;

    /**
     * 构造方法注入依赖
     *
     * @param executorFactory    请求执行器工厂
     * @param extractionExecutor 数据提取执行器
     * @param assertExecutor     断言执行器
     * @param sqlRequestExecutor SQL 请求执行器
     */
    @Autowired
    public ApiRequestServiceImpl(RequestExecutorFactory executorFactory, ExtractionExecutor extractionExecutor,
                                  ApiAssertExecutor assertExecutor, SqlRequestExecutor sqlRequestExecutor) {
        this.executorFactory = executorFactory;
        this.extractionExecutor = extractionExecutor;
        this.assertExecutor = assertExecutor;
        this.sqlRequestExecutor = sqlRequestExecutor;
    }

    @Resource
    private ApiRequestMapper apiRequestMapper;

    @Resource
    private GlobalVarMapper globalVarMapper;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    public SaResult saveOrUpdate(AddApiInterfaceDTO addApiInterfaceDTO) {
        if (addApiInterfaceDTO == null) {
            return SaResult.error("缺少参数");
        }
        String loginIdAsString = StpUtil.getLoginIdAsString();
        ApiRequest apiRequest = new ApiRequest();
        BeanUtil.copyProperties(addApiInterfaceDTO, apiRequest);
        apiRequest.setCreateUserId(Integer.parseInt(loginIdAsString));
        apiRequest.setUpdateUserId(Integer.parseInt(loginIdAsString));
        // 排序值，根据projectId算出排序值
        Integer projectId = addApiInterfaceDTO.getProjectId();
        Integer parentId = addApiInterfaceDTO.getParentId();
        QueryWrapper<ApiRequest> apiRequestQueryWrapper = new QueryWrapper<>();
        apiRequestQueryWrapper.eq("project_id", projectId).eq("parent_id", parentId);
        // 判断是否有id
        if (apiRequest.getId() == null) {
            Long sort = apiRequestMapper.selectCount(apiRequestQueryWrapper) + 1;
            apiRequest.setSort(Integer.valueOf(sort.toString()));
            apiRequest.setUpdateTime(new Date());
            apiRequest.setUpdateUserId(StpUtil.getLoginIdAsInt());
        }
        return apiRequestMapper.insertOrUpdate(apiRequest) ? SaResult.ok().setData(apiRequest.getId()) : SaResult.error("保存失败");
    }

    @Override
    public SaResult folderList(Integer projectId) {
        List<ApiFolderTreeVO> result = new ArrayList<>();
        ApiFolderTreeVO root = new ApiFolderTreeVO();
        root.setApiName("根目录");
        root.setId(0);
        root.setSort(0);
        root.setApiNode(ApiNodeType.FOLDER);
        result.add(root);

        List<ApiFolderTreeVO> apiFolderTreeVOS = apiRequestMapper.folderList(projectId);
        root.setChildren(buildTree(apiFolderTreeVOS));
        return SaResult.ok().setData(result);
    }

    @Override
    public SaResult apiListTree(Integer projectId) {
        List<ApiFolderTreeVO> result = new ArrayList<>();
        ApiFolderTreeVO root = new ApiFolderTreeVO();
        root.setApiName("根目录");
        root.setId(0);
        root.setSort(0);
        root.setApiNode(ApiNodeType.FOLDER);
        result.add(root);
        List<ApiFolderTreeVO> apiFolderTreeVOS = apiRequestMapper.apiListTree(projectId);
        root.setChildren(buildTree(apiFolderTreeVOS));
        return SaResult.ok().setData(result);
    }

    @Override
    @Transactional
    public SaResult deleteApi(Integer id) {
        // 判断是否存在
        ApiRequest apiRequest = apiRequestMapper.selectById(id);
        if (apiRequest == null) return SaResult.ok("删除成功");

        Integer projectId = apiRequest.getProjectId();

        // 判断是否是目录
        if (apiRequest.getApiNode() == ApiNodeType.FOLDER) {
            // 递归查询所有子节点
            List<ApiRequest> allChildren = apiRequestMapper.findAllChildrenIds(id);
            List<Integer> deleteIds = new ArrayList<>();
            deleteIds.add(id);
            if (allChildren != null) {
                for (ApiRequest child : allChildren) {
                    if (child != null && child.getId() != null && !child.getId().equals(id)) {
                        deleteIds.add(child.getId());
                    }
                }
            }

            // 统计要删除的用例数量（source_drat_id 不为空且不为 0）
            long caseCount = 0;
            if (!deleteIds.isEmpty()) {
                QueryWrapper<ApiRequest> caseWrapper = new QueryWrapper<>();
                caseWrapper.in("id", deleteIds)
                        .ne("source_drat_id", 0)
                        .isNotNull("source_drat_id");
                caseCount = apiRequestMapper.selectCount(caseWrapper);
            }

            // 物理删除该文件夹及子节点下的所有用例（source_drat_id 关联的用例）
            if (!deleteIds.isEmpty()) {
                QueryWrapper<ApiRequest> caseDeleteWrapper = new QueryWrapper<>();
                caseDeleteWrapper.in("source_drat_id", deleteIds);
                apiRequestMapper.delete(caseDeleteWrapper);
            }

            // 逻辑删除当前文件夹及所有子节点
            List<ApiRequest> deleteNodes = apiRequestMapper.selectBatchIds(deleteIds);
            Date now = new Date();
            for (ApiRequest node : deleteNodes) {
                if (node != null) {
                    node.setDeletedAt(now);
                    apiRequestMapper.deleteById(node);
                }
            }

            // 更新排序
            QueryWrapper<ApiRequest> apiRequestQueryWrapper = new QueryWrapper<>();
            apiRequestQueryWrapper.eq("parent_id", apiRequest.getParentId());
            List<ApiRequest> apiRequests = apiRequestMapper.selectList(apiRequestQueryWrapper);
            if (apiRequests != null && !apiRequests.isEmpty()) {
                apiRequests.forEach(api -> {
                    if (api.getSort() > apiRequest.getSort()) {
                        api.setSort(api.getSort() - 1);
                    }
                });
                apiRequestMapper.updateById(apiRequests);
            }

            // 触发项目统计更新
            if (caseCount > 0 && projectId != null) {
                eventPublisher.publishEvent(new ProjectUpdateEvent(this, -(int) caseCount, String.valueOf(projectId), UpdateDataType.API));
            }

            return SaResult.ok("删除成功");
        } else if (apiRequest.getApiNode() == ApiNodeType.INTERFACE) {
            // 如果是接口，删除当前接口下的所有关联的用例集
            QueryWrapper<ApiRequest> apiRequestQueryWrapper = new QueryWrapper<>();
            apiRequestQueryWrapper.eq("source_drat_id", id);
            long caseCount = apiRequestMapper.selectCount(apiRequestQueryWrapper);
            apiRequestMapper.delete(apiRequestQueryWrapper);
            // 更新排序
            apiRequestQueryWrapper = new QueryWrapper<>();
            apiRequestQueryWrapper.eq("parent_id", apiRequest.getParentId());
            List<ApiRequest> apiRequests = apiRequestMapper.selectList(apiRequestQueryWrapper);
            if (apiRequests != null && !apiRequests.isEmpty()) {
                apiRequests.forEach(api -> {
                    if (api.getSort() > apiRequest.getSort()) {
                        api.setSort(api.getSort() - 1);
                    }
                });
                apiRequestMapper.updateById(apiRequests);
            }

            // 触发项目统计更新：接口本身不计入 apiTotal；
            // 如果是用例（sourceDratId != null），需额外扣减 1，再加上其下用例数
            boolean isCase = apiRequest.getSourceDratId() != null && apiRequest.getSourceDratId() != 0;
            long totalCaseDelta = caseCount + (isCase ? 1 : 0);
            if (totalCaseDelta > 0 && projectId != null) {
                eventPublisher.publishEvent(new ProjectUpdateEvent(this, -(int) totalCaseDelta, String.valueOf(projectId), UpdateDataType.API));
            }

            // 逻辑删除接口本身
            apiRequest.setDeletedAt(new Date());
            return apiRequestMapper.deleteById(apiRequest) > 0 ? SaResult.ok("删除成功") : SaResult.error("删除失败");
        }
        return SaResult.error("不支持的节点类型");
    }

    @Override
    public SaResult updateApiSort(List<ApiFolderTreeVO> apiRequestVOS) {
        if (apiRequestVOS == null || apiRequestVOS.isEmpty()) return SaResult.ok("更新成功");
        // 将数据打平
        List<ApiRequest> flatApiRequest = new ArrayList<>();
        for (ApiFolderTreeVO api : apiRequestVOS) {
            ApiRequest apiRequest = new ApiRequest();
            BeanUtils.copyProperties(api, apiRequest);
            flatApiRequest.add(apiRequest);
            flatApiTree(api, flatApiRequest);
        }
        // 批量保存
        if (flatApiRequest.isEmpty()) return SaResult.ok("更新成功");
        List<BatchResult> batchResults = apiRequestMapper.updateById(flatApiRequest);
        boolean allSuccess = batchResults.stream().allMatch(result -> result.getUpdateCounts().length > 0);
        return allSuccess ? SaResult.ok("更新成功") : SaResult.error("更新失败");
    }

    @Override
    public SaResult copyApi(Integer id) {
        if (id == null) return SaResult.error("缺少参数");
        ApiRequest apiRequest = apiRequestMapper.selectById(id);
        if (apiRequest == null) return SaResult.error("接口不存在");
        ApiRequest copyApi = new ApiRequest();
        BeanUtils.copyProperties(apiRequest, copyApi);
        copyApi.setId(null);
        copyApi.setApiName(copyApi.getApiName() + "_副本");
        copyApi.setCreateTime(new Date());
        copyApi.setUpdateTime(new Date());
        copyApi.setCreateUserId(StpUtil.getLoginIdAsInt());
        copyApi.setUpdateUserId(StpUtil.getLoginIdAsInt());
        return apiRequestMapper.insert(copyApi) > 0 ? SaResult.ok("复制成功").setData(copyApi.getId()) : SaResult.error("复制失败");
    }

    /**
     * 调试执行指定接口
     *
     * 核心流程：
     *   - 根据接口 ID 查询数据库获取接口完整配置
     *   - 获取接口配置的请求方法，通过 RequestExecutorFactory 获取对应执行器
     *   - 调用 RequestExecutor 执行 HTTP 请求
     *       - 执行过程中会自动进行变量替换（URL、Header、Cookie、Query、Body）
     *       - 请求失败时返回包含错误信息的响应，不抛出异常
     *   - 如果接口配置了关联提取规则（ApiRequest），
     *       调用 ExtractionExecutor 从响应中提取数据
     *   - 将提取结果存入 TestHttpResponse 返回给前端
     *
     * 数据提取：
     *   - 支持 JSONPath、正则、Header、Cookie、状态码 5 种提取方式
     *   - 提取结果以 Map 形式返回，key 为 variableName，value 为提取值
     *   - 前端可在后续请求中通过 {{variableName}} 或 ${variableName} 引用
     *
     * @param id 接口 ID
     * @return SaResult，data 字段包含 TestHttpResponse 调试结果
     */
    @Override
    public SaResult debug(Integer id) {
        // 调试
        TestHttpResponse executeResult = null;

        ApiRequest apiRequest = apiRequestMapper.selectById(id);
        if (apiRequest == null) return SaResult.error("接口不存在");

        // 合并全局变量到请求中
        mergeGlobalVars(apiRequest);

        // 根据 apiType 选择执行器：SQL 走 SqlRequestExecutor，其他走 HTTP 工厂
        RequestExecutor executor;
        if (apiRequest.getApiType() == ApiType.SQL) {
            executor = sqlRequestExecutor;
        } else {
            RequestMethod requestMethod = apiRequest.getRequestMethod();
            executor = executorFactory.getExecutor(requestMethod);
            if (executor == null) {
                String uuid = UUID.randomUUID().toString();
                return SaResult.error().setData(TestHttpResponse.builder()
                        .uuid(uuid)
                        .status("error")
                        .errorMessage("Unsupported request method: " + requestMethod)
                        .build());
            }
        }

        executeResult = executor.execute(apiRequest);
        executeResult.setApiId(apiRequest.getId());
        executeResult.setApiName(apiRequest.getApiName());

        // 执行关联提取
        List<ApiExtraction> extractions = apiRequest.getAssociationExtraction();
        if (extractions != null && !extractions.isEmpty()) {
            Map<String, Object> extractedVariables = extractionExecutor.execute(extractions, executeResult);
            executeResult.setExtractedVariables(extractedVariables);
            // 提取详情（接口自身的提取规则来源为 API）
            List<ExtractionDetail> extractionDetails = extractionExecutor.executeWithDetails(extractions, executeResult, RuleSource.API);
            executeResult.setExtractionDetails(extractionDetails);
        }

        // 执行断言验证
        List<AssertParameter> assertions = apiRequest.getApiResultAssert();
        if (assertions != null && !assertions.isEmpty()) {
            executeResult.setAssertionResults(assertExecutor.execute(assertions, executeResult));
        }
        // 响应结构校验（响应定义开启时自动执行，结果混入 assertionResults）
        appendSchemaValidation(apiRequest, executeResult);

        return SaResult.ok().setData(executeResult);
    }

    @Override
    public SaResult getApiById(Integer id) {
        if (id == null) return SaResult.error("缺少参数");
        ApiRequest apiRequest = apiRequestMapper.selectById(id);
        if (apiRequest == null) return SaResult.error("接口不存在");
        return SaResult.ok().setData(apiRequest);
    }

    @Override
    public SaResult saveCase(AddApiInterfaceDTO addApiInterfaceDTO) {
        if (addApiInterfaceDTO == null) {
            return SaResult.error("缺少参数");
        }
        if (addApiInterfaceDTO.getSourceDratId() == null) {
            return SaResult.error("缺少来源接口ID");
        }
        String loginIdAsString = StpUtil.getLoginIdAsString();
        ApiRequest apiRequest = new ApiRequest();
        BeanUtil.copyProperties(addApiInterfaceDTO, apiRequest);
        // 清空id，作为新记录插入
        apiRequest.setId(null);
        // 用例不需要parentId和sort（不在树形列表中展示）
        apiRequest.setParentId(0);
        apiRequest.setSort(0);
        apiRequest.setCreateUserId(Integer.parseInt(loginIdAsString));
        apiRequest.setUpdateUserId(Integer.parseInt(loginIdAsString));
        apiRequest.setCreateTime(new Date());
        apiRequest.setUpdateTime(new Date());
        boolean success = apiRequestMapper.insert(apiRequest) > 0;
        if (success && apiRequest.getProjectId() != null) {
            eventPublisher.publishEvent(new ProjectUpdateEvent(this, 1, String.valueOf(apiRequest.getProjectId()), UpdateDataType.API));
        }
        return success ? SaResult.ok("保存用例成功").setData(apiRequest.getId()) : SaResult.error("保存用例失败");
    }

    @Override
    public SaResult getCases(Integer sourceId) {
        if (sourceId == null) return SaResult.error("缺少参数");
        QueryWrapper<ApiRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("source_drat_id", sourceId);
        wrapper.orderByAsc("create_time");
        List<ApiRequest> cases = apiRequestMapper.selectList(wrapper);
        return SaResult.ok().setData(cases);
    }

    @Override
    public SaResult interfaceCaseTree(Integer projectId) {
        if (projectId == null) return SaResult.error("缺少参数");

        // 1. 查询所有接口（非用例：source_drat_id 为 null 或 0）
        QueryWrapper<ApiRequest> interfaceWrapper = new QueryWrapper<>();
        interfaceWrapper.eq("project_id", projectId)
                .eq("api_node", ApiNodeType.INTERFACE)
                .and(w -> w.isNull("source_drat_id").or().eq("source_drat_id", 0))
                .orderByAsc("sort");
        List<ApiRequest> interfaces = apiRequestMapper.selectList(interfaceWrapper);

        // 2. 查询所有用例（source_drat_id 不为 null 且不为 0）
        QueryWrapper<ApiRequest> caseWrapper = new QueryWrapper<>();
        caseWrapper.eq("project_id", projectId)
                .eq("api_node", ApiNodeType.INTERFACE)
                .isNotNull("source_drat_id")
                .ne("source_drat_id", 0)
                .orderByAsc("create_time");
        List<ApiRequest> cases = apiRequestMapper.selectList(caseWrapper);

        // 3. 按 source_drat_id 分组用例
        Map<Integer, List<ApiRequest>> caseMap = cases.stream()
                .filter(c -> c.getSourceDratId() != null)
                .collect(Collectors.groupingBy(ApiRequest::getSourceDratId));

        // 4. 组装树：接口作为父节点，用例作为子节点
        List<ApiFolderTreeVO> result = new ArrayList<>();
        for (ApiRequest api : interfaces) {
            ApiFolderTreeVO interfaceNode = new ApiFolderTreeVO();
            interfaceNode.setId(api.getId());
            interfaceNode.setApiName(api.getApiName());
            interfaceNode.setApiNode(ApiNodeType.INTERFACE);
            interfaceNode.setApiType(api.getApiType() != null ? api.getApiType().name() : null);
            interfaceNode.setSort(api.getSort());

            List<ApiRequest> caseList = caseMap.getOrDefault(api.getId(), new ArrayList<>());
            List<ApiFolderTreeVO> caseNodes = caseList.stream().map(c -> {
                ApiFolderTreeVO caseNode = new ApiFolderTreeVO();
                caseNode.setId(c.getId());
                caseNode.setApiName(c.getApiName());
                caseNode.setApiNode(ApiNodeType.INTERFACE);
                caseNode.setApiType(c.getApiType() != null ? c.getApiType().name() : null);
                return caseNode;
            }).collect(Collectors.toList());

            interfaceNode.setChildren(caseNodes);
            result.add(interfaceNode);
        }

        return SaResult.ok().setData(result);
    }

    @Override
    public SaResult debugByConfig(ApiRequest apiRequest) {
        if (apiRequest == null) {
            return SaResult.error("缺少API配置参数");
        }

        // 合并全局变量到请求中
        mergeGlobalVars(apiRequest);

        // 根据 apiType 选择执行器：SQL 走 SqlRequestExecutor，其他走 HTTP 工厂
        RequestExecutor executor;
        if (apiRequest.getApiType() == ApiType.SQL) {
            executor = sqlRequestExecutor;
        } else {
            RequestMethod requestMethod = apiRequest.getRequestMethod();
            if (requestMethod == null) {
                String uuid = UUID.randomUUID().toString();
                return SaResult.error().setData(TestHttpResponse.builder()
                        .uuid(uuid)
                        .status("error")
                        .errorMessage("请求方法不能为空")
                        .build());
            }
            executor = executorFactory.getExecutor(requestMethod);
            if (executor == null) {
                String uuid = UUID.randomUUID().toString();
                return SaResult.error().setData(TestHttpResponse.builder()
                        .uuid(uuid)
                        .status("error")
                        .errorMessage("Unsupported request method: " + requestMethod)
                        .build());
            }
        }

        TestHttpResponse executeResult = executor.execute(apiRequest);
        executeResult.setApiId(apiRequest.getId());
        executeResult.setApiName(apiRequest.getApiName());

        // 执行关联提取
        List<ApiExtraction> extractions = apiRequest.getAssociationExtraction();
        if (extractions != null && !extractions.isEmpty()) {
            Map<String, Object> extractedVariables = extractionExecutor.execute(extractions, executeResult);
            executeResult.setExtractedVariables(extractedVariables);
            // 提取详情（接口自身的提取规则来源为 API）
            List<ExtractionDetail> extractionDetails = extractionExecutor.executeWithDetails(extractions, executeResult, RuleSource.API);
            executeResult.setExtractionDetails(extractionDetails);
        }

        // 执行断言验证
        List<AssertParameter> assertions = apiRequest.getApiResultAssert();
        if (assertions != null && !assertions.isEmpty()) {
            executeResult.setAssertionResults(assertExecutor.execute(assertions, executeResult));
        }
        // 响应结构校验（响应定义开启时自动执行，结果混入 assertionResults）
        appendSchemaValidation(apiRequest, executeResult);

        return SaResult.ok().setData(executeResult);
    }

    /**
     * 响应结构校验：接口配置了响应定义且开启校验时，对响应体做 schema 校验，
     * 结果以 assertType=SCHEMA 的 AssertResult 追加到 assertionResults。
     */
    private void appendSchemaValidation(ApiRequest apiRequest, TestHttpResponse executeResult) {
        try {
            AssertResult schemaResult = SchemaValidator.validate(apiRequest.getResponseSchema(), executeResult);
            if (schemaResult == null) {
                return;
            }
            if (executeResult.getAssertionResults() == null) {
                executeResult.setAssertionResults(new ArrayList<>());
            }
            executeResult.getAssertionResults().add(schemaResult);
        } catch (Exception e) {
            log.warn("响应结构校验执行异常: {}", e.getMessage());
        }
    }

    private void flatApiTree(ApiFolderTreeVO parentVO, List<ApiRequest> result) {
        if (parentVO.getChildren() != null && !parentVO.getChildren().isEmpty()) {
            // 递归添加
            for (ApiFolderTreeVO apiFolderTreeVO : parentVO.getChildren()) {
                ApiRequest apiRequest = new ApiRequest();
                BeanUtils.copyProperties(apiFolderTreeVO, apiRequest);
                result.add(apiRequest);
                flatApiTree(apiFolderTreeVO, result);
            }
        }

    }


    /**
     * 构建树形结构并排序
     */
    private List<ApiFolderTreeVO> buildTree(List<ApiFolderTreeVO> nodes) {
        if (CollectionUtils.isEmpty(nodes)) {
            return new ArrayList<>();
        }

        Map<Integer, ApiFolderTreeVO> nodeMap = nodes.stream()
                .collect(Collectors.toMap(ApiFolderTreeVO::getId, node -> {
                    node.setChildren(new ArrayList<>());
                    return node;
                }));

        List<ApiFolderTreeVO> roots = new ArrayList<>();
        for (ApiFolderTreeVO node : nodes) {
            if (node.getParentId() == 0) {
                roots.add(node);
            } else {
                ApiFolderTreeVO parent = nodeMap.get(node.getParentId());
                if (parent != null) {
                    parent.getChildren().add(node);
                }
            }
        }

        // 根节点排序
        roots.sort(Comparator.comparing(ApiFolderTreeVO::getSort,
                Comparator.nullsLast(Integer::compareTo)));
        // 递归排序子节点
        roots.forEach(this::sortTree);

        return roots;
    }

    /**
     * 递归排序
     */
    private void sortTree(ApiFolderTreeVO node) {
        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
            node.getChildren().sort(Comparator.comparing(ApiFolderTreeVO::getSort,
                    Comparator.nullsLast(Integer::compareTo)));
            node.getChildren().forEach(childNode -> sortTree(childNode));
        }
    }

    /**
     * 合并全局变量到请求中
     *
     * 功能说明：在请求执行前，读取当前团队下所有启用的全局变量，
     * 按类型合并到 ApiRequest 的 envInfo 中，供后续变量替换和请求发送使用
     *
     * 优先级规则（从低到高）：
     *   全局参数（global_var） < 环境参数（environment） < 接口参数（api_request）
     *
     * 合并策略：
     *   - VARIABLE 类型：合并到 envVariables Map 中，环境变量可覆盖同名全局变量
     *   - HEADER 类型：转换为 RequestParameter 列表，追加到 envHeaders 列表前面
     *   - COOKIE 类型：转换为 RequestParameter 列表，追加到 envCookies 列表前面
     *
     * 注意：此方法仅修改内存中的 ApiRequest 对象，不会写入数据库
     *
     * @param apiRequest 接口请求配置对象
     */
    private void mergeGlobalVars(ApiRequest apiRequest) {
        if (apiRequest.getTeamId() == null || globalVarMapper == null) {
            return;
        }

        QueryWrapper<GlobalVar> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("team_id", apiRequest.getTeamId())
                .eq("disabled", false);
        List<GlobalVar> globalVars = globalVarMapper.selectList(queryWrapper);

        if (globalVars == null || globalVars.isEmpty()) {
            return;
        }

        // 确保 envInfo 不为 null
        if (apiRequest.getEnvInfo() == null) {
            apiRequest.setEnvInfo(new RequestExecuteInfo());
        }
        RequestExecuteInfo envInfo = apiRequest.getEnvInfo();

        // 1. 合并 VARIABLE 类型的全局变量到 envVariables
        // 优先级：全局变量 < 环境变量，所以全局变量先 put，环境变量后 put（覆盖）
        Map<String, String> mergedVariables = new HashMap<>();
        for (GlobalVar globalVar : globalVars) {
            if (globalVar.getType() == GlobalRequestVarType.VARIABLE
                    && globalVar.getName() != null) {
                mergedVariables.put(globalVar.getName(), globalVar.getValue());
            }
        }
        // 再将原有的环境变量合并进去（环境变量优先级更高，覆盖同名全局变量）
        if (envInfo.getEnvVariables() != null) {
            mergedVariables.putAll(envInfo.getEnvVariables());
        }
        if (!mergedVariables.isEmpty()) {
            envInfo.setEnvVariables(mergedVariables);
        }

        // 2. 合并 HEADER 类型的全局变量到 envHeaders
        // 优先级：全局 Header < 环境 Header < 接口 Header
        // 在列表中的顺序：全局 Header 在前（先被 addHeader），环境 Header 在后
        List<RequestParameter> mergedHeaders = new ArrayList<>();
        for (GlobalVar globalVar : globalVars) {
            if (globalVar.getType() == GlobalRequestVarType.HEADER
                    && globalVar.getName() != null) {
                RequestParameter header = new RequestParameter();
                header.setName(globalVar.getName());
                header.setValue(globalVar.getValue());
                header.setDisabled(false);
                mergedHeaders.add(header);
            }
        }
        // 将原有的环境 Header 追加到后面（环境 Header 优先级更高）
        if (envInfo.getEnvHeaders() != null) {
            mergedHeaders.addAll(envInfo.getEnvHeaders());
        }
        if (!mergedHeaders.isEmpty()) {
            envInfo.setEnvHeaders(mergedHeaders);
        }

        // 3. 合并 COOKIE 类型的全局变量到 envCookies
        // 优先级：全局 Cookie < 环境 Cookie < 接口 Cookie
        List<RequestParameter> mergedCookies = new ArrayList<>();
        for (GlobalVar globalVar : globalVars) {
            if (globalVar.getType() == GlobalRequestVarType.COOKIE
                    && globalVar.getName() != null) {
                RequestParameter cookie = new RequestParameter();
                cookie.setName(globalVar.getName());
                cookie.setValue(globalVar.getValue());
                cookie.setDisabled(false);
                mergedCookies.add(cookie);
            }
        }
        // 将原有的环境 Cookie 追加到后面（环境 Cookie 优先级更高）
        if (envInfo.getEnvCookies() != null) {
            mergedCookies.addAll(envInfo.getEnvCookies());
        }
        if (!mergedCookies.isEmpty()) {
            envInfo.setEnvCookies(mergedCookies);
        }

        // 4. 合并 ASSERT 类型的全局断言到 apiResultAssert
        // 优先级：全局断言 < 接口断言（接口断言后执行，可覆盖全局断言结果）
        List<AssertParameter> mergedAssertions = new ArrayList<>();
        for (GlobalVar globalVar : globalVars) {
            if (globalVar.getType() == GlobalRequestVarType.ASSERT
                    && globalVar.getGlobalAssert() != null) {
                // globalAssert 是 List<AssertParameter>，将其中的断言规则添加到合并列表
                for (AssertParameter assertParam : globalVar.getGlobalAssert()) {
                    if (assertParam != null) {
                        assertParam.setSource(RuleSource.GLOBAL);
                        mergedAssertions.add(assertParam);
                    }
                }
            }
        }
        // 将接口自身的断言追加到后面（接口断言优先级更高）
        if (apiRequest.getApiResultAssert() != null) {
            for (AssertParameter assertParam : apiRequest.getApiResultAssert()) {
                if (assertParam != null) {
                    assertParam.setSource(RuleSource.API);
                    mergedAssertions.add(assertParam);
                }
            }
        }
        if (!mergedAssertions.isEmpty()) {
            apiRequest.setApiResultAssert(mergedAssertions);
        }
    }

    // ==================== Swagger / OpenAPI 导入 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult importSwagger(SwaggerImportDTO dto, MultipartFile file) {
        if (dto == null || dto.getProjectId() == null) {
            return SaResult.error("缺少项目ID");
        }
        if ((file == null || file.isEmpty()) && StringUtils.isBlank(dto.getUrl())) {
            return SaResult.error("请上传 Swagger 文件或填写 URL");
        }

        OpenAPI openApi;
        try {
            ParseOptions options = new ParseOptions();
            options.setResolve(true);
            OpenAPIV3Parser parser = new OpenAPIV3Parser();
            SwaggerParseResult result;
            if (file != null && !file.isEmpty()) {
                String content = new String(file.getBytes(), StandardCharsets.UTF_8);
                result = parser.readContents(content, null, options);
            } else {
                result = parser.readLocation(dto.getUrl(), null, options);
            }
            if (result == null || result.getOpenAPI() == null) {
                String messages = result == null || result.getMessages() == null
                        ? "解析失败" : String.join("; ", result.getMessages());
                return SaResult.error("Swagger 解析失败：" + messages);
            }
            openApi = result.getOpenAPI();
        } catch (Exception e) {
            return SaResult.error("Swagger 解析异常：" + e.getMessage());
        }

        String baseUrl = resolveBaseUrl(openApi);
        Integer projectId = dto.getProjectId();
        Integer teamId = dto.getTeamId() == null ? 0 : dto.getTeamId();
        Integer rootParentId = dto.getParentId() == null ? 0 : dto.getParentId();
        boolean groupByTags = dto.getGroupByTags() == null || dto.getGroupByTags();

        SwaggerImportResultVO resultVO = new SwaggerImportResultVO();
        Map<String, ApiRequest> folderCache = new HashMap<>();
        AtomicInteger newFolderCount = new AtomicInteger(0);
        Map<Integer, Integer> folderSortCounter = new HashMap<>();
        Map<Integer, Integer> interfaceSortCounter = new HashMap<>();
        List<ApiRequest> interfacesToSave = new ArrayList<>();
        List<ApiRequest> interfacesToUpdate = new ArrayList<>();

        if (openApi.getPaths() == null || openApi.getPaths().isEmpty()) {
            return SaResult.ok("未解析到任何接口").setData(resultVO);
        }

        int currentUserId = StpUtil.getLoginIdAsInt();
        Date now = new Date();

        for (Map.Entry<String, PathItem> pathEntry : openApi.getPaths().entrySet()) {
            String rawPath = pathEntry.getKey();
            PathItem pathItem = pathEntry.getValue();
            if (pathItem == null || pathItem.readOperationsMap() == null) {
                continue;
            }
            Map<PathItem.HttpMethod, Operation> ops = pathItem.readOperationsMap();
            for (Map.Entry<PathItem.HttpMethod, Operation> opEntry : ops.entrySet()) {
                PathItem.HttpMethod httpMethod = opEntry.getKey();
                Operation operation = opEntry.getValue();
                RequestMethod method = convertHttpMethod(httpMethod);
                if (method == null) {
                    resultVO.addSkipped(rawPath, httpMethod.name(), "不支持的请求方法");
                    continue;
                }

                String requestPath = replacePathVariables(rawPath);
                String apiName = buildApiName(operation, method, rawPath);

                Integer parentId = rootParentId;
                if (groupByTags) {
                    String folderName = extractFolderName(operation);
                    ApiRequest folder = getOrCreateFolder(folderName, rootParentId, projectId, teamId,
                            folderCache, folderSortCounter, newFolderCount, currentUserId, now);
                    parentId = folder.getId();
                }

                ApiRequest existing = findExisting(projectId, parentId, method, requestPath);
                if (existing != null) {
                    if (!Boolean.TRUE.equals(dto.getOverwrite())) {
                        resultVO.addSkipped(rawPath, httpMethod.name(), "接口已存在");
                        continue;
                    }
                    // 覆盖更新：保留 id、sort、创建人/创建时间、来源、父目录
                    // 仅更新接口定义相关字段，不覆盖用户手动配置的断言/提取/脚本
                    existing.setApiName(apiName);
                    existing.setRequestMethod(method);
                    existing.setRequestPath(requestPath);
                    existing.setRequestHeader(convertParameters(pathItem.getParameters(), operation.getParameters(), "header"));
                    existing.setQuery(convertParameters(pathItem.getParameters(), operation.getParameters(), "query"));
                    existing.setCookies(convertParameters(pathItem.getParameters(), operation.getParameters(), "cookie"));
                    existing.setBody(buildBody(operation.getRequestBody()));
                    List<ApiResponseExample> responseExamples = buildResponseExamples(operation);
                    existing.setResponseExamples(responseExamples);
                    existing.setMockResponse(buildMockResponse(responseExamples));
                    existing.setUpdateUserId(currentUserId);
                    existing.setUpdateTime(now);
                    if (StringUtils.isNotBlank(baseUrl)) {
                        RequestExecuteInfo envInfo = new RequestExecuteInfo();
                        envInfo.setBaseUrl(baseUrl);
                        existing.setEnvInfo(envInfo);
                    }
                    interfacesToUpdate.add(existing);
                    resultVO.addOverwritten();
                    continue;
                }

                ApiRequest api = new ApiRequest();
                api.setParentId(parentId);
                api.setProjectId(projectId);
                api.setTeamId(teamId);
                api.setApiName(apiName);
                api.setApiNode(ApiNodeType.INTERFACE);
                api.setApiType(ApiType.HTTP);
                api.setRequestMethod(method);
                api.setRequestPath(requestPath);
                api.setRequestHeader(convertParameters(pathItem.getParameters(), operation.getParameters(), "header"));
                api.setQuery(convertParameters(pathItem.getParameters(), operation.getParameters(), "query"));
                api.setCookies(convertParameters(pathItem.getParameters(), operation.getParameters(), "cookie"));
                api.setBody(buildBody(operation.getRequestBody()));
                List<ApiResponseExample> responseExamples = buildResponseExamples(operation);
                api.setResponseExamples(responseExamples);
                api.setMockResponse(buildMockResponse(responseExamples));
                api.setAssociationExtraction(new ArrayList<>());
                api.setApiResultAssert(new ArrayList<>());
                api.setPreScript(new ArrayList<>());
                api.setPostScript(new ArrayList<>());
                api.setSort(nextInterfaceSort(parentId, projectId, interfaceSortCounter));
                api.setCreateUserId(currentUserId);
                api.setUpdateUserId(currentUserId);
                api.setCreateTime(now);
                api.setUpdateTime(now);

                if (StringUtils.isNotBlank(baseUrl)) {
                    RequestExecuteInfo envInfo = new RequestExecuteInfo();
                    envInfo.setBaseUrl(baseUrl);
                    api.setEnvInfo(envInfo);
                }

                interfacesToSave.add(api);
            }
        }

        if (!interfacesToSave.isEmpty()) {
            this.saveBatch(interfacesToSave, 100);
        }
        if (!interfacesToUpdate.isEmpty()) {
            this.updateBatchById(interfacesToUpdate, 100);
        }

        resultVO.setInterfaceCount(interfacesToSave.size());
        resultVO.setOverwrittenCount(interfacesToUpdate.size());
        resultVO.setFolderCount(newFolderCount.get());
        return SaResult.ok("导入成功").setData(resultVO);
    }

    private String resolveBaseUrl(OpenAPI openApi) {
        if (openApi == null || openApi.getServers() == null || openApi.getServers().isEmpty()) {
            return null;
        }
        Server server = openApi.getServers().get(0);
        if (server == null || StringUtils.isBlank(server.getUrl())) {
            return null;
        }
        return replacePathVariables(server.getUrl());
    }

    private RequestMethod convertHttpMethod(PathItem.HttpMethod httpMethod) {
        if (httpMethod == null) {
            return null;
        }
        return switch (httpMethod) {
            case GET -> RequestMethod.GET;
            case POST -> RequestMethod.POST;
            case PUT -> RequestMethod.PUT;
            case DELETE -> RequestMethod.DELETE;
            default -> null;
        };
    }

    private String replacePathVariables(String path) {
        if (path == null) {
            return null;
        }
        return path.replaceAll("\\{([^}]+)\\}", "{{$1}}");
    }

    private String buildApiName(Operation operation, RequestMethod method, String path) {
        String name = null;
        if (operation != null) {
            if (StringUtils.isNotBlank(operation.getSummary())) {
                name = operation.getSummary();
            } else if (StringUtils.isNotBlank(operation.getOperationId())) {
                name = operation.getOperationId();
            }
        }
        if (StringUtils.isBlank(name)) {
            name = method.name() + " " + path;
        }
        return StringUtils.abbreviate(name, 100);
    }

    private String extractFolderName(Operation operation) {
        if (operation != null && operation.getTags() != null && !operation.getTags().isEmpty()) {
            return StringUtils.abbreviate(operation.getTags().get(0), 100);
        }
        return "未分类";
    }

    private ApiRequest getOrCreateFolder(String name, Integer parentId, Integer projectId, Integer teamId,
                                         Map<String, ApiRequest> folderCache, Map<Integer, Integer> folderSortCounter,
                                         AtomicInteger newFolderCount, int currentUserId, Date now) {
        String cacheKey = parentId + ":" + name;
        ApiRequest folder = folderCache.get(cacheKey);
        if (folder != null) {
            return folder;
        }

        QueryWrapper<ApiRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId)
                .eq("parent_id", parentId)
                .eq("api_node", ApiNodeType.FOLDER)
                .eq("api_name", name)
                .eq("is_deleted", 0)
                .last("limit 1");
        folder = apiRequestMapper.selectOne(wrapper);

        if (folder == null) {
            folder = new ApiRequest();
            folder.setParentId(parentId);
            folder.setProjectId(projectId);
            folder.setTeamId(teamId);
            folder.setApiName(name);
            folder.setApiNode(ApiNodeType.FOLDER);
            folder.setApiType(ApiType.HTTP);
            folder.setSort(nextFolderSort(parentId, projectId, folderSortCounter));
            folder.setCreateUserId(currentUserId);
            folder.setUpdateUserId(currentUserId);
            folder.setCreateTime(now);
            folder.setUpdateTime(now);
            apiRequestMapper.insert(folder);
            newFolderCount.incrementAndGet();
        }

        folderCache.put(cacheKey, folder);
        return folder;
    }

    private int nextFolderSort(Integer parentId, Integer projectId, Map<Integer, Integer> counter) {
        Integer current = counter.get(parentId);
        if (current == null) {
            QueryWrapper<ApiRequest> wrapper = new QueryWrapper<>();
            wrapper.eq("project_id", projectId)
                    .eq("parent_id", parentId)
                    .eq("api_node", ApiNodeType.FOLDER)
                    .eq("is_deleted", 0);
            current = apiRequestMapper.selectCount(wrapper).intValue();
        }
        current = current + 1;
        counter.put(parentId, current);
        return current;
    }

    private int nextInterfaceSort(Integer parentId, Integer projectId, Map<Integer, Integer> counter) {
        Integer current = counter.get(parentId);
        if (current == null) {
            QueryWrapper<ApiRequest> wrapper = new QueryWrapper<>();
            wrapper.eq("project_id", projectId)
                    .eq("parent_id", parentId)
                    .eq("api_node", ApiNodeType.INTERFACE)
                    .eq("is_deleted", 0);
            current = apiRequestMapper.selectCount(wrapper).intValue();
        }
        current = current + 1;
        counter.put(parentId, current);
        return current;
    }

    private boolean isDuplicate(Integer projectId, Integer parentId, RequestMethod method, String requestPath) {
        return findExisting(projectId, parentId, method, requestPath) != null;
    }

    private ApiRequest findExisting(Integer projectId, Integer parentId, RequestMethod method, String requestPath) {
        QueryWrapper<ApiRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId)
                .eq("parent_id", parentId)
                .eq("api_node", ApiNodeType.INTERFACE)
                .eq("request_method", method)
                .eq("request_path", requestPath)
                .eq("is_deleted", 0)
                .last("limit 1");
        return apiRequestMapper.selectOne(wrapper);
    }

    private List<RequestParameter> convertParameters(List<Parameter> pathParams, List<Parameter> opParams, String in) {
        List<Parameter> merged = new ArrayList<>();
        if (pathParams != null) {
            merged.addAll(pathParams);
        }
        if (opParams != null) {
            // 同名的 operation 参数覆盖 path 级别参数
            for (Parameter opParam : opParams) {
                if (opParam == null || opParam.getIn() == null) {
                    continue;
                }
                merged.removeIf(p -> in.equalsIgnoreCase(p.getIn()) && Objects.equals(p.getName(), opParam.getName()));
                merged.add(opParam);
            }
        }

        List<RequestParameter> result = new ArrayList<>();
        for (Parameter param : merged) {
            if (param == null || !in.equalsIgnoreCase(param.getIn())) {
                continue;
            }
            RequestParameter rp = new RequestParameter();
            rp.setName(param.getName());
            rp.setValue(extractParameterExample(param));
            rp.setType(mapParameterType(param.getSchema()));
            rp.setDescription(param.getDescription());
            rp.setDisabled(false);
            result.add(rp);
        }
        return result.isEmpty() ? null : result;
    }

    private ParameterType mapParameterType(Schema<?> schema) {
        if (schema == null || StringUtils.isBlank(schema.getType())) {
            return ParameterType.STRING;
        }
        return switch (schema.getType().toLowerCase()) {
            case "integer" -> ParameterType.INTEGER;
            case "number" -> ParameterType.NUMBER;
            case "boolean" -> ParameterType.BOOLEAN;
            case "array" -> ParameterType.ARRAY;
            case "object" -> ParameterType.JSON;
            case "file" -> ParameterType.FILE;
            default -> ParameterType.STRING;
        };
    }

    private String extractExample(Schema<?> schema) {
        if (schema == null) {
            return "";
        }
        if (schema.getExample() != null) {
            return String.valueOf(schema.getExample());
        }
        if (schema.getDefault() != null) {
            return String.valueOf(schema.getDefault());
        }
        return "";
    }

    private String extractParameterExample(Parameter param) {
        if (param == null) {
            return "";
        }
        if (param.getExample() != null) {
            return String.valueOf(param.getExample());
        }
        return extractExample(param.getSchema());
    }

    private Body buildBody(RequestBody requestBody) {
        Body body = new Body();
        if (requestBody == null || requestBody.getContent() == null || requestBody.getContent().isEmpty()) {
            body.setMode(BodyMode.NONE);
            return body;
        }

        Map<String, MediaType> content = requestBody.getContent();
        List<String> priority = Arrays.asList(
                "application/json",
                "application/x-www-form-urlencoded",
                "multipart/form-data",
                "application/xml",
                "text/xml",
                "text/plain"
        );

        MediaType selected = null;
        String selectedType = null;
        for (String type : priority) {
            if (content.containsKey(type)) {
                selected = content.get(type);
                selectedType = type;
                break;
            }
        }
        if (selected == null) {
            Map.Entry<String, MediaType> first = content.entrySet().iterator().next();
            selected = first.getValue();
            selectedType = first.getKey();
        }

        Schema<?> schema = selected.getSchema();
        Object mediaTypeExample = selected.getExample();
        if ("application/json".equals(selectedType)) {
            body.setMode(BodyMode.JSON);
            Object example = mediaTypeExample != null ? mediaTypeExample : buildExample(schema);
            try {
                if (example instanceof String) {
                    body.setJson((String) example);
                } else {
                    body.setJson(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(example));
                }
            } catch (Exception e) {
                body.setJson("{}");
            }
        } else if ("application/xml".equals(selectedType) || "text/xml".equals(selectedType)) {
            body.setMode(BodyMode.XML);
            if (mediaTypeExample instanceof String) {
                body.setXml((String) mediaTypeExample);
            } else {
                Object example = buildExample(schema);
                body.setXml(mapToXml("root", example));
            }
        } else if ("application/x-www-form-urlencoded".equals(selectedType)) {
            body.setMode(BodyMode.X_WWW_FORM_URLENCODED);
            body.setXWwwFormUrlencoded(schemaToFormParams(schema));
        } else if ("multipart/form-data".equals(selectedType)) {
            body.setMode(BodyMode.FORM_DATA);
            body.setFormData(schemaToFormParams(schema));
        } else {
            body.setMode(BodyMode.NONE);
        }
        return body;
    }

    private List<ApiResponseExample> buildResponseExamples(Operation operation) {
        if (operation == null || operation.getResponses() == null || operation.getResponses().isEmpty()) {
            return new ArrayList<>();
        }

        List<ApiResponseExample> examples = new ArrayList<>();
        for (Map.Entry<String, ApiResponse> entry : operation.getResponses().entrySet()) {
            String code = entry.getKey();
            // 跳过 default 等非数字状态码
            if (!StringUtils.isNumeric(code)) {
                continue;
            }
            ApiResponse response = entry.getValue();
            if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
                continue;
            }

            Map<String, MediaType> content = response.getContent();
            List<String> priority = Arrays.asList(
                    "application/json",
                    "application/xml",
                    "text/xml",
                    "text/plain"
            );

            MediaType selected = null;
            String selectedType = null;
            for (String type : priority) {
                if (content.containsKey(type)) {
                    selected = content.get(type);
                    selectedType = type;
                    break;
                }
            }
            if (selected == null) {
                Map.Entry<String, MediaType> first = content.entrySet().iterator().next();
                selected = first.getValue();
                selectedType = first.getKey();
            }

            ApiResponseExample example = new ApiResponseExample();
            example.setStatusCode(parseResponseCode(code));
            example.setDescription(response.getDescription());
            example.setContentType(selectedType);
            example.setHeaders(convertResponseHeaders(response.getHeaders()));
            example.setBody(resolveResponseBody(selected, selectedType));
            example.setBodyMode("RAW");
            examples.add(example);
        }

        examples.sort(Comparator.comparingInt(ApiResponseExample::getStatusCode));
        return examples;
    }

    private List<RequestParameter> convertResponseHeaders(Map<String, Header> headers) {
        if (headers == null || headers.isEmpty()) {
            return new ArrayList<>();
        }
        List<RequestParameter> result = new ArrayList<>();
        headers.forEach((name, header) -> {
            if (header == null) {
                return;
            }
            RequestParameter rp = new RequestParameter();
            rp.setName(name);
            rp.setValue(String.valueOf(extractHeaderExample(header)));
            rp.setDescription(header.getDescription());
            rp.setDisabled(false);
            result.add(rp);
        });
        return result;
    }

    private Object extractHeaderExample(Header header) {
        if (header == null) {
            return null;
        }
        if (header.getExample() != null) {
            return header.getExample();
        }
        Map<String, Example> examples = header.getExamples();
        if (examples != null && !examples.isEmpty()) {
            Example first = examples.values().iterator().next();
            if (first != null) {
                return first.getValue();
            }
        }
        return extractExample(header.getSchema());
    }

    private String resolveResponseBody(MediaType selected, String selectedType) {
        if (selected == null) {
            return "";
        }
        Object example = selected.getExample();
        if (example == null) {
            Map<String, Example> examples = selected.getExamples();
            if (examples != null && !examples.isEmpty()) {
                Example first = examples.values().iterator().next();
                if (first != null) {
                    example = first.getValue();
                }
            }
        }
        if (example == null) {
            example = buildExample(selected.getSchema());
        }

        if (example instanceof String) {
            return (String) example;
        }
        if ("application/xml".equals(selectedType) || "text/xml".equals(selectedType)) {
            return mapToXml("root", example);
        }
        try {
            return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(example);
        } catch (Exception e) {
            return "{}";
        }
    }

    private MockResponse buildMockResponse(List<ApiResponseExample> examples) {
        if (examples == null || examples.isEmpty()) {
            return null;
        }
        ApiResponseExample first2xx = examples.stream()
                .filter(e -> e.getStatusCode() >= 200 && e.getStatusCode() < 300)
                .findFirst()
                .orElse(null);
        if (first2xx == null) {
            return null;
        }

        MockResponse mock = new MockResponse();
        mock.setEnabled(false);
        mock.setBodyMode("RAW");
        mock.setStatusCode(first2xx.getStatusCode());
        mock.setBody(first2xx.getBody());
        if (first2xx.getHeaders() != null) {
            mock.setHeaders(new ArrayList<>(first2xx.getHeaders()));
        }
        return mock;
    }

    private int parseResponseCode(String code) {
        if (StringUtils.isBlank(code)) {
            return 200;
        }
        try {
            return Integer.parseInt(code);
        } catch (NumberFormatException e) {
            return 200;
        }
    }

    private Object buildExample(Schema<?> schema) {
        if (schema == null) {
            return "";
        }
        if (schema.getExample() != null) {
            return schema.getExample();
        }

        String type = StringUtils.defaultString(schema.getType(), "").toLowerCase();
        return switch (type) {
            case "object" -> buildObjectExample(schema);
            case "array" -> buildArrayExample(schema);
            case "integer" -> 0;
            case "number" -> 0.0;
            case "boolean" -> false;
            case "file" -> "";
            default -> "string";
        };
    }

    private Object buildObjectExample(Schema<?> schema) {
        Map<String, Object> map = new LinkedHashMap<>();
        Map<String, Schema> properties = schema.getProperties();
        if (properties != null) {
            properties.forEach((key, value) -> map.put(key, buildExample(value)));
        }
        return map;
    }

    private List<Object> buildArrayExample(Schema<?> schema) {
        List<Object> list = new ArrayList<>();
        if (schema.getItems() != null) {
            list.add(buildExample(schema.getItems()));
        }
        return list;
    }

    private List<RequestParameter> schemaToFormParams(Schema<?> schema) {
        List<RequestParameter> list = new ArrayList<>();
        if (schema == null) {
            return list;
        }
        Map<String, Schema> properties = schema.getProperties();
        if (properties == null) {
            return list;
        }
        properties.forEach((key, value) -> {
            RequestParameter rp = new RequestParameter();
            rp.setName(key);
            rp.setValue(extractExample(value));
            rp.setType(mapParameterType(value));
            rp.setDescription(value.getDescription());
            rp.setDisabled(false);
            list.add(rp);
        });
        return list;
    }

    private String mapToXml(String rootName, Object value) {
        if (value == null) {
            return "<" + rootName + "/>";
        }
        StringBuilder sb = new StringBuilder();
        if (value instanceof Map) {
            sb.append("<").append(rootName).append(">");
            ((Map<?, ?>) value).forEach((k, v) -> {
                if (k != null) {
                    sb.append(mapToXml(String.valueOf(k), v));
                }
            });
            sb.append("</").append(rootName).append(">");
        } else if (value instanceof List) {
            sb.append("<").append(rootName).append(">");
            for (Object item : (List<?>) value) {
                sb.append(mapToXml("item", item));
            }
            sb.append("</").append(rootName).append(">");
        } else {
            String text = escapeXml(String.valueOf(value));
            sb.append("<").append(rootName).append(">")
                    .append(text)
                    .append("</").append(rootName).append(">");
        }
        return sb.toString();
    }

    private String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
