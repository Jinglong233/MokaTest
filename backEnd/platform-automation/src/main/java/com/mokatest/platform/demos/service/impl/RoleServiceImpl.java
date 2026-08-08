package com.mokatest.platform.demos.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.domain.ui.Permission;
import com.mokatest.platform.demos.domain.ui.Role;
import com.mokatest.platform.demos.domain.ui.RolePermission;
import com.mokatest.platform.demos.domain.ui.TeamMember;
import com.mokatest.platform.demos.domain.ui.User;
import com.mokatest.platform.demos.mapper.PermissionMapper;
import com.mokatest.platform.demos.mapper.RoleMapper;
import com.mokatest.platform.demos.mapper.RolePermissionMapper;
import com.mokatest.platform.demos.mapper.TeamMemberMapper;
import com.mokatest.platform.demos.mapper.UserMapper;
import com.mokatest.platform.demos.service.RoleService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class RoleServiceImpl implements RoleService {

    @Resource
    private RoleMapper roleMapper;

    @Resource
    private RolePermissionMapper rolePermissionMapper;

    @Resource
    private PermissionMapper permissionMapper;

    @Resource
    private TeamMemberMapper teamMemberMapper;

    @Resource
    private UserMapper userMapper;

    @Override
    public SaResult getRoleList(Long teamId) {
        QueryWrapper<Role> wrapper = new QueryWrapper<>();
        // 权限模板是平台级功能：未指定团队时返回所有角色（系统角色 + 所有团队模板角色）
        if (teamId != null) {
            wrapper.and(w -> w.isNull("team_id").or().eq("team_id", teamId));
        }
        wrapper.orderByDesc("is_system").orderByAsc("create_time");
        return SaResult.ok().setData(roleMapper.selectList(wrapper));
    }

    @Override
    public SaResult getRoleDetail(Long id) {
        if (id == null) {
            return SaResult.error("缺少角色ID");
        }
        Role role = roleMapper.selectById(id);
        if (role == null) {
            return SaResult.error("角色不存在");
        }
        return SaResult.ok().setData(role);
    }

    @Override
    @Transactional
    public SaResult createRole(Role role) {
        if (!isSuperAdminCurrentUser()) {
            return SaResult.error("仅超级管理员可创建角色");
        }
        if (role == null || role.getName() == null || role.getCode() == null) {
            return SaResult.error("缺少角色信息");
        }
        // 同一团队下角色编码不能重复
        QueryWrapper<Role> wrapper = new QueryWrapper<>();
        wrapper.eq("code", role.getCode());
        wrapper.and(w -> w.isNull("team_id").or().eq("team_id", role.getTeamId()));
        if (roleMapper.selectCount(wrapper) > 0) {
            return SaResult.error("角色编码已存在");
        }
        role.setIsSystem(0);
        // 自定义角色一律为项目级模板（TEMPLATE）：只用于项目内执行权限分配，不信任前端传值
        role.setScopeType("TEMPLATE");
        roleMapper.insert(role);
        return SaResult.ok("创建成功").setData(role);
    }

    @Override
    @Transactional
    public SaResult updateRole(Role role) {
        if (!isSuperAdminCurrentUser()) {
            return SaResult.error("仅超级管理员可修改角色");
        }
        if (role == null || role.getId() == null) {
            return SaResult.error("缺少角色ID");
        }
        Role exist = roleMapper.selectById(role.getId());
        if (exist == null) {
            return SaResult.error("角色不存在");
        }
        // 系统预设角色不可修改
        if (exist.getIsSystem() != null && exist.getIsSystem() == 1) {
            return SaResult.error("系统预设角色不可修改");
        }
        roleMapper.updateById(role);
        return SaResult.ok("更新成功");
    }

    @Override
    @Transactional
    public SaResult deleteRole(Long id) {
        if (!isSuperAdminCurrentUser()) {
            return SaResult.error("仅超级管理员可删除角色");
        }
        if (id == null) {
            return SaResult.error("缺少角色ID");
        }
        Role exist = roleMapper.selectById(id);
        if (exist == null) {
            return SaResult.error("角色不存在");
        }
        if (exist.getIsSystem() != null && exist.getIsSystem() == 1) {
            return SaResult.error("系统预设角色不能删除");
        }
        // 角色已被团队成员使用时禁止删除，避免产生孤儿 role_id
        QueryWrapper<TeamMember> tmWrapper = new QueryWrapper<>();
        tmWrapper.eq("role_id", id);
        if (teamMemberMapper.selectCount(tmWrapper) > 0) {
            return SaResult.error("该角色已被团队成员使用，无法删除");
        }
        roleMapper.deleteById(id);
        // 删除角色权限关联
        QueryWrapper<RolePermission> wrapper = new QueryWrapper<>();
        wrapper.eq("role_id", id);
        rolePermissionMapper.delete(wrapper);
        return SaResult.ok("删除成功");
    }

    @Override
    public SaResult getRolePermissions(Long roleId) {
        if (roleId == null) {
            return SaResult.error("缺少角色ID");
        }
        QueryWrapper<RolePermission> wrapper = new QueryWrapper<>();
        wrapper.eq("role_id", roleId);
        List<RolePermission> list = rolePermissionMapper.selectList(wrapper);
        List<Long> permissionIds = list.stream().map(RolePermission::getPermissionId).toList();
        return SaResult.ok().setData(permissionIds);
    }

    @Override
    @Transactional
    public SaResult assignPermissions(Long roleId, List<Long> permissionIds) {
        if (!isSuperAdminCurrentUser()) {
            return SaResult.error("仅超级管理员可分配权限");
        }
        if (roleId == null) {
            return SaResult.error("缺少角色ID");
        }
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            return SaResult.error("角色不存在");
        }
        // 系统预设角色权限由系统硬编码固定，不通过 role_permission 配置，禁止此处修改
        if (role.getIsSystem() != null && role.getIsSystem() == 1) {
            return SaResult.error("系统预设角色权限由系统固定，不可修改");
        }

        // 自定义模板(TEMPLATE)只能分配项目执行类权限：
        // 排除 platform 平台级、team 团队级，以及项目管理类(项目创建/编辑/删除、成员管理)，仅保留 project:view 等执行权限
        if ("TEMPLATE".equals(role.getScopeType())) {
            if (permissionIds != null && !permissionIds.isEmpty()) {
                List<Permission> permissions = permissionMapper.selectBatchIds(permissionIds);
                for (Permission p : permissions) {
                    if (p == null || p.getCode() == null) continue;
                    String code = p.getCode();
                    boolean disallowed =
                            code.startsWith("platform:") || code.equals("platform")
                            || code.startsWith("team:") || code.equals("team")
                            || ((code.startsWith("project:") || code.equals("project")) && !code.equals("project:view"));
                    if (disallowed) {
                        return SaResult.error("模板只能分配项目执行类权限，不允许：" + code);
                    }
                }
            }
        } else if (role.getTeamId() != null) {
            // 兼容旧的团队自定义角色：不能分配平台级权限（platform: 前缀）
            if (permissionIds != null && !permissionIds.isEmpty()) {
                List<Permission> permissions = permissionMapper.selectBatchIds(permissionIds);
                for (Permission p : permissions) {
                    if (p != null && p.getCode() != null && p.getCode().startsWith("platform:")) {
                        return SaResult.error("团队角色不能分配平台级权限：" + p.getCode());
                    }
                }
            }
        }

        // 删除旧关联
        QueryWrapper<RolePermission> wrapper = new QueryWrapper<>();
        wrapper.eq("role_id", roleId);
        rolePermissionMapper.delete(wrapper);

        // 插入新关联
        if (permissionIds != null && !permissionIds.isEmpty()) {
            Set<Long> uniqueIds = new HashSet<>(permissionIds);
            for (Long pid : uniqueIds) {
                if (pid == null) continue;
                RolePermission rp = new RolePermission();
                rp.setRoleId(roleId);
                rp.setPermissionId(pid);
                rolePermissionMapper.insert(rp);
            }
        }
        return SaResult.ok("权限分配成功");
    }

    /**
     * 判断当前登录用户是否为超级管理员
     */
    private boolean isSuperAdminCurrentUser() {
        String userId = StpUtil.getLoginIdAsString();
        User user = userMapper.selectById(userId);
        return user != null && "super_admin".equals(user.getRole());
    }
}
