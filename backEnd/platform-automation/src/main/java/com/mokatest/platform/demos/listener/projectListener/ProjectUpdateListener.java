package com.mokatest.platform.demos.listener.projectListener;


import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.mokatest.platform.demos.listener.projectListener.Enum.UpdateDataType;
import com.mokatest.platform.demos.mapper.ProjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ProjectUpdateListener {

    @Resource
    private ProjectMapper projectMapper;

    /**
     * 监听项目资源变更事件，使用数据库原子 SQL 更新统计字段，避免并发覆盖
     */
    @Async
    @EventListener
    public void handleCourseChange(ProjectUpdateEvent event) {
        try {
            Integer updateNumber = event.getUpdateNumber();
            String projectId = event.getProjectId();
            UpdateDataType updateDataType = event.getUpdateDataType();

            if (projectId == null || updateNumber == null || updateNumber == 0 || updateDataType == null) {
                return;
            }

            UpdateWrapper<com.mokatest.platform.demos.domain.ui.Project> wrapper = new UpdateWrapper<>();
            wrapper.eq("id", projectId);

            switch (updateDataType) {
                case API:
                    wrapper.setSql("api_total = api_total + " + updateNumber);
                    break;
                case UI:
                    wrapper.setSql("ui_total = ui_total + " + updateNumber);
                    break;
                case PERFORMANCE:
                    wrapper.setSql("performance_total = performance_total + " + updateNumber);
                    break;
                case PLAN:
                    wrapper.setSql("plan_total = plan_total + " + updateNumber);
                    break;
                default:
                    return;
            }

            int affected = projectMapper.update(null, wrapper);
            if (affected <= 0) {
                log.error("项目数据联动更新失败, projectId={}, type={}, number={}", projectId, updateDataType, updateNumber);
            }
        } catch (Exception e) {
            log.error("更新项目概览统计失败", e);
        }
    }

}
