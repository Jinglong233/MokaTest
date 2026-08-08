package com.mokatest.platform.demos.api.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.api.domain.DataTemplate;
import com.mokatest.platform.demos.api.domain.apiEnum.DataTemplateNodeType;
import com.mokatest.platform.demos.api.domain.requestModel.MockFieldRule;
import com.mokatest.platform.demos.api.domain.vo.DataTemplateTreeNode;
import com.mokatest.platform.demos.api.mapper.DataTemplateMapper;
import com.mokatest.platform.demos.api.service.DataTemplateService;
import com.mokatest.platform.demos.service.ProjectPermissionChecker;
import com.mokatest.platform.demos.util.MockRuleGenerator;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * 数据模板 Service 实现（单表化改造后）
 *
 * @author JingLong
 * @since 2026-06-17
 */
@Service
@RequiredArgsConstructor
public class DataTemplateServiceImpl extends ServiceImpl<DataTemplateMapper, DataTemplate>
        implements DataTemplateService {

    @Resource
    private ProjectPermissionChecker permissionChecker;

    /**
     * 校验当前登录用户是否有指定项目的访问权限
     */
    private boolean hasProjectAccess(Integer projectId) {
        if (projectId == null) {
            return false;
        }
        String loginId = StpUtil.getLoginIdAsString();
        return permissionChecker.isSuperAdmin(loginId)
                || permissionChecker.hasPermissionByProjectId(projectId, loginId);
    }

    @Override
    public SaResult saveOrUpdateTemplate(DataTemplate dataTemplate) {
        if (dataTemplate == null) {
            return SaResult.error("模板数据不能为空");
        }
        if (dataTemplate.getProjectId() == null) {
            return SaResult.error("所属项目不能为空");
        }
        if (dataTemplate.getTeamId() == null) {
            return SaResult.error("所属团队不能为空");
        }
        if (!hasProjectAccess(dataTemplate.getProjectId())) {
            return SaResult.error("无权限访问该项目");
        }
        if (dataTemplate.getTemplateName() == null || dataTemplate.getTemplateName().trim().isEmpty()) {
            return SaResult.error("模板名称不能为空");
        }

        Integer parentId = dataTemplate.getParentId() != null ? dataTemplate.getParentId() : 0;
        dataTemplate.setParentId(parentId);
        if (parentId > 0) {
            DataTemplate parent = baseMapper.selectById(parentId);
            if (parent == null || parent.getIsDeleted() == 1 || parent.getNodeType() != DataTemplateNodeType.FOLDER) {
                return SaResult.error("所属文件夹不存在");
            }
            if (!parent.getProjectId().equals(dataTemplate.getProjectId())) {
                return SaResult.error("所属文件夹与模板不在同一项目");
            }
        }

        // 继承校验：父模板必须存在、同项目、非自身、继承链不成环
        Integer extendsId = dataTemplate.getExtendsId();
        if (extendsId != null) {
            if (extendsId.equals(dataTemplate.getId())) {
                return SaResult.error("模板不能继承自身");
            }
            DataTemplate ext = baseMapper.selectById(extendsId);
            if (ext == null || ext.getNodeType() != DataTemplateNodeType.TEMPLATE) {
                return SaResult.error("继承的父模板不存在");
            }
            if (!ext.getProjectId().equals(dataTemplate.getProjectId())) {
                return SaResult.error("继承的父模板与模板不在同一项目");
            }
            if (extendsChainContains(extendsId, dataTemplate.getId())) {
                return SaResult.error("模板继承存在循环引用");
            }
        }

        Integer userId = StpUtil.getLoginIdAsInt();
        Date now = new Date();
        dataTemplate.setNodeType(DataTemplateNodeType.TEMPLATE);
        dataTemplate.setTemplateName(dataTemplate.getTemplateName().trim());

        if (dataTemplate.getId() == null) {
            if (isTemplateNameExists(dataTemplate.getProjectId(), parentId, dataTemplate.getTemplateName(), null)) {
                return SaResult.error("该目录下已存在同名模板");
            }
            dataTemplate.setCreateUserId(userId);
            dataTemplate.setCreateTime(now);
            dataTemplate.setUpdateUserId(userId);
            dataTemplate.setUpdateTime(now);
            if (dataTemplate.getIsShared() == null) {
                dataTemplate.setIsShared(1);
            }
            if (dataTemplate.getSort() == null) {
                dataTemplate.setSort(calcNextSort(dataTemplate.getProjectId(), parentId));
            }
            baseMapper.insert(dataTemplate);
        } else {
            DataTemplate existed = baseMapper.selectById(dataTemplate.getId());
            if (existed == null) {
                return SaResult.error("模板不存在");
            }
            if (existed.getNodeType() != DataTemplateNodeType.TEMPLATE) {
                return SaResult.error("该节点不是模板");
            }
            // 数据边界：更新必须同时对模板原所属项目有权限，防止把他人项目的模板改挂到自己项目
            if (!existed.getProjectId().equals(dataTemplate.getProjectId())
                    && !hasProjectAccess(existed.getProjectId())) {
                return SaResult.error("无权限访问该模板原所属项目");
            }
            if (isTemplateNameExists(dataTemplate.getProjectId(), parentId, dataTemplate.getTemplateName(), dataTemplate.getId())) {
                return SaResult.error("该目录下已存在同名模板");
            }
            dataTemplate.setUpdateUserId(userId);
            dataTemplate.setUpdateTime(now);
            dataTemplate.setCreateUserId(null);
            dataTemplate.setCreateTime(null);
            dataTemplate.setNodeType(DataTemplateNodeType.TEMPLATE);
            baseMapper.updateById(dataTemplate);
        }
        return SaResult.ok("保存成功").setData(dataTemplate);
    }

    @Override
    public SaResult saveOrUpdateFolder(DataTemplate folder) {
        if (folder == null) {
            return SaResult.error("文件夹数据不能为空");
        }
        if (folder.getProjectId() == null) {
            return SaResult.error("所属项目不能为空");
        }
        if (!hasProjectAccess(folder.getProjectId())) {
            return SaResult.error("无权限访问该项目");
        }
        if (folder.getTemplateName() == null || folder.getTemplateName().trim().isEmpty()) {
            return SaResult.error("文件夹名称不能为空");
        }

        Integer parentId = folder.getParentId() != null ? folder.getParentId() : 0;
        folder.setParentId(parentId);
        folder.setTemplateName(folder.getTemplateName().trim());

        if (folder.getId() != null) {
            if (folder.getId().equals(parentId)) {
                return SaResult.error("不能将文件夹设置为自身子级");
            }
            if (isDescendant(folder.getId(), parentId)) {
                return SaResult.error("不能将文件夹移动到自身或其子文件夹内");
            }
            DataTemplate existed = baseMapper.selectById(folder.getId());
            if (existed == null || existed.getNodeType() != DataTemplateNodeType.FOLDER) {
                return SaResult.error("文件夹不存在");
            }
        }

        if (parentId > 0) {
            DataTemplate parent = baseMapper.selectById(parentId);
            if (parent == null || parent.getIsDeleted() == 1 || parent.getNodeType() != DataTemplateNodeType.FOLDER) {
                return SaResult.error("父文件夹不存在");
            }
            if (!parent.getProjectId().equals(folder.getProjectId())) {
                return SaResult.error("父文件夹与当前文件夹不在同一项目");
            }
        }

        if (hasDuplicateFolderName(folder.getProjectId(), parentId, folder.getTemplateName(), folder.getId())) {
            return SaResult.error("该目录下已存在同名文件夹");
        }

        Integer userId = StpUtil.getLoginIdAsInt();
        Date now = new Date();
        folder.setNodeType(DataTemplateNodeType.FOLDER);
        folder.setTemplateSchema(null);
        folder.setIsShared(null);
        folder.setDescription(null);

        if (folder.getId() == null) {
            folder.setCreateUserId(userId);
            folder.setCreateTime(now);
            folder.setUpdateUserId(userId);
            folder.setUpdateTime(now);
            if (folder.getSort() == null) {
                folder.setSort(calcNextSort(folder.getProjectId(), parentId));
            }
            baseMapper.insert(folder);
        } else {
            folder.setUpdateUserId(userId);
            folder.setUpdateTime(now);
            folder.setCreateUserId(null);
            folder.setCreateTime(null);
            baseMapper.updateById(folder);
        }
        return SaResult.ok("保存成功").setData(folder);
    }

    @Override
    public SaResult listByProject(Integer projectId) {
        if (projectId == null) {
            return SaResult.error("项目id不能为空");
        }
        if (!hasProjectAccess(projectId)) {
            return SaResult.error("无权限访问该项目");
        }
        QueryWrapper<DataTemplate> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId)
                .eq("node_type", DataTemplateNodeType.TEMPLATE.name())
                .eq("is_deleted", 0)
                .orderByDesc("update_time");
        List<DataTemplate> list = baseMapper.selectList(wrapper);
        return SaResult.ok().setData(list);
    }

    @Override
    public SaResult folderList(Integer projectId) {
        if (projectId == null) {
            return SaResult.error("项目id不能为空");
        }
        if (!hasProjectAccess(projectId)) {
            return SaResult.error("无权限访问该项目");
        }
        List<DataTemplate> folders = baseMapper.folderList(projectId);
        List<DataTemplateTreeNode> tree = buildTree(folders, true);
        DataTemplateTreeNode root = new DataTemplateTreeNode();
        root.setId(0);
        root.setName("根目录");
        root.setParentId(0);
        root.setNodeType(DataTemplateNodeType.FOLDER);
        root.setSort(0);
        root.setChildren(tree);
        return SaResult.ok().setData(List.of(root));
    }

    @Override
    public SaResult tree(Integer projectId) {
        if (projectId == null) {
            return SaResult.error("项目id不能为空");
        }
        if (!hasProjectAccess(projectId)) {
            return SaResult.error("无权限访问该项目");
        }
        List<DataTemplate> nodes = baseMapper.treeList(projectId);
        List<DataTemplateTreeNode> tree = buildTree(nodes, false);
        DataTemplateTreeNode root = new DataTemplateTreeNode();
        root.setId(0);
        root.setName("根目录");
        root.setParentId(0);
        root.setNodeType(DataTemplateNodeType.FOLDER);
        root.setSort(0);
        root.setChildren(tree);
        return SaResult.ok().setData(List.of(root));
    }

    @Override
    public SaResult getById(Integer id) {
        if (id == null) {
            return SaResult.error("模板id不能为空");
        }
        DataTemplate template = baseMapper.selectById(id);
        if (template == null) {
            return SaResult.error("模板不存在");
        }
        if (!hasProjectAccess(template.getProjectId())) {
            return SaResult.error("无权限访问该模板");
        }
        return SaResult.ok().setData(template);
    }

    @Override
    public SaResult copyTemplate(Integer id) {
        if (id == null) {
            return SaResult.error("模板id不能为空");
        }
        DataTemplate source = baseMapper.selectById(id);
        if (source == null || source.getNodeType() != DataTemplateNodeType.TEMPLATE) {
            return SaResult.error("模板不存在或不是数据模板");
        }
        if (!hasProjectAccess(source.getProjectId())) {
            return SaResult.error("无权限访问该模板");
        }

        DataTemplate copy = new DataTemplate();
        copy.setTeamId(source.getTeamId());
        copy.setProjectId(source.getProjectId());
        copy.setParentId(source.getParentId());
        copy.setNodeType(DataTemplateNodeType.TEMPLATE);
        copy.setTemplateName(generateCopyName(source.getProjectId(), source.getParentId(), source.getTemplateName()));
        copy.setDescription(source.getDescription());
        copy.setTemplateSchema(source.getTemplateSchema());
        copy.setIsShared(source.getIsShared());
        copy.setSort(calcNextSort(source.getProjectId(), source.getParentId()));

        Integer userId = StpUtil.getLoginIdAsInt();
        Date now = new Date();
        copy.setCreateUserId(userId);
        copy.setCreateTime(now);
        copy.setUpdateUserId(userId);
        copy.setUpdateTime(now);

        baseMapper.insert(copy);
        return SaResult.ok("复制成功").setData(copy);
    }

    private String generateCopyName(Integer projectId, Integer parentId, String originalName) {
        String baseName = originalName + " 副本";
        int suffix = 1;
        String candidate = baseName;
        while (isTemplateNameExists(projectId, parentId, candidate, null)) {
            suffix++;
            candidate = baseName + "(" + suffix + ")";
        }
        return candidate;
    }

    private boolean isTemplateNameExists(Integer projectId, Integer parentId, String name, Integer excludeId) {
        QueryWrapper<DataTemplate> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId)
                .eq("parent_id", parentId != null ? parentId : 0)
                .eq("node_type", DataTemplateNodeType.TEMPLATE.name())
                .eq("template_name", name)
                .eq("is_deleted", 0);
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        return baseMapper.selectCount(wrapper) > 0;
    }

    @Override
    @Transactional
    public SaResult deleteById(Integer id) {
        if (id == null) {
            return SaResult.error("id不能为空");
        }
        DataTemplate node = baseMapper.selectById(id);
        if (node == null) {
            return SaResult.error("节点不存在");
        }
        if (!hasProjectAccess(node.getProjectId())) {
            return SaResult.error("无权限访问该节点");
        }

        Integer parentId = node.getParentId();
        Integer sort = node.getSort();
        Date now = new Date();

        if (node.getNodeType() == DataTemplateNodeType.FOLDER) {
            List<Integer> childrenIds = baseMapper.findAllChildrenIds(id);
            if (!CollectionUtils.isEmpty(childrenIds)) {
                for (Integer childId : childrenIds) {
                    DataTemplate update = new DataTemplate();
                    update.setId(childId);
                    update.setIsDeleted(1);
                    update.setDeletedAt(now);
                    update.setUpdateTime(now);
                    update.setUpdateUserId(StpUtil.getLoginIdAsInt());
                    baseMapper.updateById(update);
                }
            }
        } else {
            DataTemplate update = new DataTemplate();
            update.setId(id);
            update.setIsDeleted(1);
            update.setDeletedAt(now);
            update.setUpdateTime(now);
            update.setUpdateUserId(StpUtil.getLoginIdAsInt());
            baseMapper.updateById(update);
        }

        if (sort != null) {
            baseMapper.decrementSortAfter(parentId, sort);
        }
        return SaResult.ok("删除成功");
    }

    @Override
    @Transactional
    public SaResult updateSort(List<DataTemplateTreeNode> treeNodes) {
        if (CollectionUtils.isEmpty(treeNodes)) {
            return SaResult.ok("排序完成");
        }
        List<DataTemplate> flatList = new ArrayList<>();
        flattenTree(treeNodes, 0, flatList);

        // 数据边界：先全量加载并校验所有节点属于同一项目且当前用户有权限，
        // 不能只校验第一个节点，否则可在 payload 混入其他项目节点实现跨项目移动
        Map<Integer, DataTemplate> existedMap = new HashMap<>();
        Integer projectId = null;
        for (DataTemplate node : flatList) {
            if (node.getId() == null || node.getId() == 0) {
                continue;
            }
            DataTemplate existed = baseMapper.selectById(node.getId());
            if (existed == null) {
                continue;
            }
            if (projectId == null) {
                projectId = existed.getProjectId();
            } else if (!projectId.equals(existed.getProjectId())) {
                return SaResult.error("排序节点必须属于同一项目");
            }
            existedMap.put(existed.getId(), existed);
        }
        if (projectId == null || !hasProjectAccess(projectId)) {
            return SaResult.error("无权限访问该项目");
        }

        for (DataTemplate node : flatList) {
            if (node.getId() == null || node.getId() == 0) {
                continue;
            }
            DataTemplate existed = existedMap.get(node.getId());
            if (existed == null) {
                continue;
            }
            if (node.getParentId() != null && node.getParentId() > 0) {
                DataTemplate parent = existedMap.get(node.getParentId());
                if (parent == null) {
                    parent = baseMapper.selectById(node.getParentId());
                }
                if (parent == null || parent.getIsDeleted() == 1 || parent.getNodeType() != DataTemplateNodeType.FOLDER) {
                    return SaResult.error("父文件夹不存在");
                }
                if (!projectId.equals(parent.getProjectId())) {
                    return SaResult.error("父文件夹与节点不在同一项目");
                }
            }
            if (existed.getNodeType() == DataTemplateNodeType.FOLDER) {
                if (isDescendant(existed.getId(), node.getParentId())) {
                    return SaResult.error("不能将文件夹移动到自身或其子文件夹内");
                }
            }
            DataTemplate update = new DataTemplate();
            update.setId(node.getId());
            update.setParentId(node.getParentId());
            update.setSort(node.getSort());
            update.setUpdateTime(new Date());
            update.setUpdateUserId(StpUtil.getLoginIdAsInt());
            baseMapper.updateById(update);
        }
        return SaResult.ok("排序完成");
    }

    @Override
    public SaResult generate(Integer id) {
        DataTemplate template = baseMapper.selectById(id);
        if (template == null || template.getNodeType() != DataTemplateNodeType.TEMPLATE) {
            return SaResult.error("模板不存在");
        }
        if (!hasProjectAccess(template.getProjectId())) {
            return SaResult.error("无权限访问该模板");
        }
        Map<String, Object> data = generateSingle(template);
        return SaResult.ok().setData(data);
    }

    @Override
    public SaResult batchGenerate(Integer id, Integer count) {
        if (count == null || count <= 0) {
            count = 1;
        }
        if (count > 1000) {
            return SaResult.error("单次生成数量不能超过 1000");
        }
        DataTemplate template = baseMapper.selectById(id);
        if (template == null || template.getNodeType() != DataTemplateNodeType.TEMPLATE) {
            return SaResult.error("模板不存在");
        }
        if (!hasProjectAccess(template.getProjectId())) {
            return SaResult.error("无权限访问该模板");
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(generateSingle(template));
        }
        return SaResult.ok().setData(list);
    }

    @Override
    public void batchGenerateExport(Integer id, Integer count, String format, HttpServletResponse response) throws IOException {
        if (count == null || count <= 0) {
            count = 1;
        }
        if (count > 1000) {
            count = 1000;
        }
        DataTemplate template = baseMapper.selectById(id);
        if (template == null || template.getNodeType() != DataTemplateNodeType.TEMPLATE) {
            response.setContentType("application/json;charset=utf-8");
            response.getWriter().write("{\"code\":500,\"msg\":\"模板不存在\"}");
            return;
        }
        if (!hasProjectAccess(template.getProjectId())) {
            response.setContentType("application/json;charset=utf-8");
            response.getWriter().write("{\"code\":403,\"msg\":\"无权限访问该模板\"}");
            return;
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(generateSingle(template));
        }

        String safeFormat = format != null ? format.trim().toUpperCase() : "JSON";
        String fileName = template.getTemplateName() + "_" + count + "_" + System.currentTimeMillis();

        switch (safeFormat) {
            case "CSV" -> {
                response.setContentType("text/csv;charset=utf-8");
                response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".csv");
                try (PrintWriter writer = response.getWriter()) {
                    if (!list.isEmpty()) {
                        List<String> keys = new ArrayList<>(list.get(0).keySet());
                        writer.println(String.join(",", keys));
                        for (Map<String, Object> row : list) {
                            List<String> values = new ArrayList<>();
                            for (String key : keys) {
                                values.add(escapeCsvValue(row.get(key)));
                            }
                            writer.println(String.join(",", values));
                        }
                    }
                }
            }
            case "EXCEL" -> {
                cn.hutool.poi.excel.ExcelWriter writer = cn.hutool.poi.excel.ExcelUtil.getWriter();
                if (!list.isEmpty()) {
                    writer.write(list, true);
                    writer.autoSizeColumnAll();
                }
                response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
                response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");
                writer.flush(response.getOutputStream());
                writer.close();
            }
            default -> {
                response.setContentType("application/json;charset=utf-8");
                response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".json");
                response.getWriter().write(com.alibaba.fastjson.JSON.toJSONString(list, true));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> generateSingle(DataTemplate template) {
        MockFieldRule root = template.getTemplateSchema();
        if (root == null) {
            return new LinkedHashMap<>();
        }
        Object result = MockRuleGenerator.generateValue(root);
        if (result instanceof Map) {
            return (Map<String, Object>) result;
        }
        Map<String, Object> wrap = new LinkedHashMap<>();
        wrap.put("value", result);
        return wrap;
    }

    private List<DataTemplateTreeNode> buildTree(List<DataTemplate> nodes, boolean folderOnly) {
        Map<Integer, DataTemplateTreeNode> nodeMap = new LinkedHashMap<>();
        for (DataTemplate node : nodes) {
            if (folderOnly && node.getNodeType() != DataTemplateNodeType.FOLDER) {
                continue;
            }
            DataTemplateTreeNode vo = convertToTreeNode(node);
            vo.setChildren(new ArrayList<>());
            nodeMap.put(vo.getId(), vo);
        }

        List<DataTemplateTreeNode> roots = new ArrayList<>();
        for (DataTemplateTreeNode node : nodeMap.values()) {
            Integer parentId = node.getParentId() != null ? node.getParentId() : 0;
            if (parentId == 0 || !nodeMap.containsKey(parentId)) {
                roots.add(node);
            } else {
                nodeMap.get(parentId).getChildren().add(node);
            }
        }

        roots.sort(Comparator.comparing(DataTemplateTreeNode::getSort, Comparator.nullsLast(Integer::compareTo)));
        for (DataTemplateTreeNode root : roots) {
            sortChildren(root);
            computeTemplateCount(root);
        }
        return roots;
    }

    private void sortChildren(DataTemplateTreeNode node) {
        if (node.getChildren() == null) return;
        node.getChildren().sort(Comparator.comparing(DataTemplateTreeNode::getSort, Comparator.nullsLast(Integer::compareTo)));
        for (DataTemplateTreeNode child : node.getChildren()) {
            sortChildren(child);
        }
    }

    private int computeTemplateCount(DataTemplateTreeNode node) {
        int count = 0;
        if (node.getNodeType() == DataTemplateNodeType.TEMPLATE) {
            count = 1;
        }
        if (node.getChildren() != null) {
            for (DataTemplateTreeNode child : node.getChildren()) {
                count += computeTemplateCount(child);
            }
        }
        if (node.getNodeType() == DataTemplateNodeType.FOLDER) {
            node.setTemplateCount(count);
        }
        return count;
    }

    private DataTemplateTreeNode convertToTreeNode(DataTemplate node) {
        DataTemplateTreeNode vo = new DataTemplateTreeNode();
        vo.setId(node.getId());
        vo.setName(node.getTemplateName());
        vo.setParentId(node.getParentId());
        vo.setNodeType(node.getNodeType());
        vo.setDescription(node.getDescription());
        vo.setIsShared(node.getIsShared());
        vo.setUpdateTime(node.getUpdateTime());
        vo.setSort(node.getSort());
        return vo;
    }

    private void flattenTree(List<DataTemplateTreeNode> nodes, Integer parentId, List<DataTemplate> result) {
        if (CollectionUtils.isEmpty(nodes)) return;
        for (int i = 0; i < nodes.size(); i++) {
            DataTemplateTreeNode node = nodes.get(i);
            if (node.getId() == null || node.getId() == 0) continue;
            DataTemplate dt = new DataTemplate();
            dt.setId(node.getId());
            dt.setParentId(parentId);
            dt.setSort(i);
            result.add(dt);
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                flattenTree(node.getChildren(), node.getId(), result);
            }
        }
    }

    private int calcNextSort(Integer projectId, Integer parentId) {
        QueryWrapper<DataTemplate> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId)
                .eq("parent_id", parentId != null ? parentId : 0)
                .eq("is_deleted", 0)
                .orderByDesc("sort")
                .last("LIMIT 1");
        DataTemplate last = baseMapper.selectOne(wrapper);
        return last != null && last.getSort() != null ? last.getSort() + 1 : 0;
    }

    private boolean hasDuplicateFolderName(Integer projectId, Integer parentId, String name, Integer excludeId) {
        QueryWrapper<DataTemplate> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId)
                .eq("parent_id", parentId != null ? parentId : 0)
                .eq("node_type", DataTemplateNodeType.FOLDER.name())
                .eq("template_name", name)
                .eq("is_deleted", 0);
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        return baseMapper.selectCount(wrapper) > 0;
    }

    /**
     * 沿继承链上溯，检查链中是否出现 targetId（用于成环检测；targetId 为 null 即新建时不可能成环）
     */
    private boolean extendsChainContains(Integer extendsId, Integer targetId) {
        if (targetId == null) {
            return false;
        }
        Set<Integer> visited = new HashSet<>();
        Integer cur = extendsId;
        while (cur != null && visited.add(cur)) {
            if (cur.equals(targetId)) {
                return true;
            }
            DataTemplate node = baseMapper.selectById(cur);
            cur = node != null ? node.getExtendsId() : null;
        }
        return false;
    }

    private boolean isDescendant(Integer ancestorId, Integer descendantId) {
        if (ancestorId == null || descendantId == null) {
            return false;
        }
        if (ancestorId.equals(descendantId)) {
            return true;
        }
        if (descendantId == 0) {
            return false;
        }
        DataTemplate folder = baseMapper.selectById(descendantId);
        if (folder == null || folder.getIsDeleted() == 1 || folder.getNodeType() != DataTemplateNodeType.FOLDER) {
            return false;
        }
        Integer parentId = folder.getParentId();
        if (parentId == null || parentId == 0) {
            return false;
        }
        return isDescendant(ancestorId, parentId);
    }

    private String escapeCsvValue(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
