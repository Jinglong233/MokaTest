package com.mokatest.platform.demos.service.impl;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.domain.ui.Permission;
import com.mokatest.platform.demos.mapper.PermissionMapper;
import com.mokatest.platform.demos.service.PermissionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PermissionServiceImpl implements PermissionService {

    @Resource
    private PermissionMapper permissionMapper;

    @Override
    public SaResult getPermissionTree() {
        QueryWrapper<Permission> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("sort");
        List<Permission> all = permissionMapper.selectList(wrapper);

        List<Map<String, Object>> result = new ArrayList<>();
        Map<Long, Map<String, Object>> nodeMap = new HashMap<>();

        for (Permission p : all) {
            Map<String, Object> node = new HashMap<>();
            node.put("id", p.getId());
            node.put("name", p.getName());
            node.put("code", p.getCode());
            node.put("type", p.getType());
            node.put("parentId", p.getParentId());
            node.put("sort", p.getSort());
            node.put("children", new ArrayList<Map<String, Object>>());
            nodeMap.put(p.getId(), node);
        }

        for (Permission p : all) {
            Map<String, Object> node = nodeMap.get(p.getId());
            if (p.getParentId() == null) {
                result.add(node);
            } else {
                Map<String, Object> parent = nodeMap.get(p.getParentId());
                if (parent != null) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> children = (List<Map<String, Object>>) parent.get("children");
                    children.add(node);
                }
            }
        }
        return SaResult.ok().setData(result);
    }

    @Override
    public SaResult getAllPermissions() {
        QueryWrapper<Permission> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("sort");
        return SaResult.ok().setData(permissionMapper.selectList(wrapper));
    }
}
