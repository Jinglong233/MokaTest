package com.mokatest.platform.demos.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mokatest.platform.demos.domain.ui.PlanWebhook;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Webhook 配置 Mapper 接口
 * 
 * 继承 MyBatis Plus 的 {@link BaseMapper}，提供基础的 CRUD 能力。
 * 额外提供按项目 ID 查询列表的方法。
 */
@Mapper
public interface PlanWebhookMapper extends BaseMapper<PlanWebhook> {

    /**
     * 根据项目ID查询所有 Webhook 配置
     * 
     * 执行通知时会调用此方法，获取该项目下全部配置后按 enabled 状态过滤。
     *
     * @param projectId 项目ID
     * @return 该项目下的 Webhook 配置列表（包含已禁用和已启用的）
     */
    @Select("SELECT * FROM plan_webhook WHERE project_id = #{projectId} AND is_deleted = 0 ORDER BY create_time DESC")
    List<PlanWebhook> selectByProjectId(@Param("projectId") Integer projectId);
}
