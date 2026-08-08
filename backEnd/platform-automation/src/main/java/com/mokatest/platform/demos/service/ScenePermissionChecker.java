package com.mokatest.platform.demos.service;

import com.mokatest.platform.demos.domain.ui.Project;
import com.mokatest.platform.demos.domain.ui.Scene;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 场景权限校验器
 *
 * 校验规则：当前登录用户必须是场景所属项目的创建者。
 * 由于目前系统没有项目成员表，项目创建者即为唯一拥有者。
 */
@Component
public class ScenePermissionChecker {

    @Autowired
    private SceneService sceneService;

    @Autowired
    private ProjectService projectService;

    /**
     * 校验用户是否有权限操作指定场景
     *
     * @param sceneId 场景ID
     * @param loginId 当前登录用户ID
     * @return true: 有权限, false: 无权限
     */
    public boolean hasPermission(Integer sceneId, String loginId) {
        if (sceneId == null || loginId == null) {
            return false;
        }
        Scene scene = sceneService.getById(sceneId);
        if (scene == null || scene.getProjectId() == null) {
            return false;
        }
        try {
            Project project = projectService.getById(Integer.valueOf(scene.getProjectId()));
            return project != null && loginId.equals(project.getCreateUserId());
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
