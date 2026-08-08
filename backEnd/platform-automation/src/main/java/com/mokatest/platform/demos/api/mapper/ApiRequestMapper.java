package com.mokatest.platform.demos.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mokatest.platform.demos.api.domain.ApiRequest;
import com.mokatest.platform.demos.api.domain.vo.ApiFolderTreeVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
* @author: JingLong
* @description 针对表【api_request(接口表)】的数据库操作Mapper
* @createDate 2026-04-03 11:16:56
* @Entity generator.domain.ApiRequest
*/
public interface ApiRequestMapper extends BaseMapper<ApiRequest> {

    /**
     * 递归查询所有目录列表(扁平化的数据)
     */
    @Select("WITH RECURSIVE folder_tree AS (\n" +
            "    -- 顶层节点\n" +
            "    SELECT id,\n" +
            "           parent_id,\n" +
            "           project_id,\n" +
            "           team_id,\n" +
            "           api_name,\n" +
            "           api_node,\n" +
            "           request_method,\n" +
            "           api_type,\n" +
            "           sql_config,\n" +
            "           sort,\n" +
            "           create_time,\n" +
            "           create_user_id,\n" +
            "           update_time,\n" +
            "           update_user_id\n" +
            "    FROM api_request\n" +
            "    WHERE api_node = 'FOLDER'\n" +
            "      AND parent_id = 0\n" +
            "      AND project_id = #{projectId}\n" +
            "      AND (source_drat_id IS NULL OR source_drat_id = 0)\n" +
            "      AND is_deleted = 0\n" +
            "\n" +
            "    UNION ALL\n" +
            "\n" +
            "    SELECT t.id,\n" +
            "           t.parent_id,\n" +
            "           t.project_id,\n" +
            "           t.team_id,\n" +
            "           t.api_name,\n" +
            "           t.api_node,\n" +
            "           t.request_method,\n" +
            "           t.api_type,\n" +
            "           t.sql_config,\n" +
            "           t.sort,\n" +
            "           t.create_time,\n" +
            "           t.create_user_id,\n" +
            "           t.update_time,\n" +
            "           t.update_user_id\n" +
            "    FROM api_request t\n" +
            "             INNER JOIN folder_tree ft ON t.parent_id = ft.id\n" +
            "    WHERE t.api_node = 'FOLDER'\n" +
            "      AND t.project_id = ft.project_id\n" +
            "      AND (t.source_drat_id IS NULL OR t.source_drat_id = 0)\n" +
            "      AND t.is_deleted = 0\n" +
            ")\n" +
            "SELECT id,\n" +
            "       parent_id,\n" +
            "       project_id,\n" +
            "       team_id,\n" +
            "       api_name,\n" +
            "       api_node,\n" +
            "       request_method,\n" +
            "       api_type,\n" +
            "       sql_config,\n" +
            "       sort,\n" +
            "       create_time,\n" +
            "       create_user_id,\n" +
            "       update_time,\n" +
            "       update_user_id\n" +
            "FROM folder_tree;")
    List<ApiFolderTreeVO> folderList(@Param("projectId") Integer projectId);


    @Select("WITH RECURSIVE folder_tree AS (\n" +
            "    -- 顶层节点\n" +
            "    SELECT id,\n" +
            "           parent_id,\n" +
            "           project_id,\n" +
            "           team_id,\n" +
            "           api_name,\n" +
            "           api_node,\n" +
            "           request_method,\n" +
            "           api_type,\n" +
            "           sql_config,\n" +
            "           sort,\n" +
            "           create_time,\n" +
            "           create_user_id,\n" +
            "           update_time,\n" +
            "           update_user_id\n" +
            "    FROM api_request\n" +
            "    WHERE parent_id = 0\n" +
            "      AND project_id = #{projectId}\n" +
            "      AND (source_drat_id IS NULL OR source_drat_id = 0)\n" +
            "      AND is_deleted = 0\n" +
            "\n" +
            "    UNION ALL\n" +
            "\n" +
            "    SELECT t.id,\n" +
            "           t.parent_id,\n" +
            "           t.project_id,\n" +
            "           t.team_id,\n" +
            "           t.api_name,\n" +
            "           t.api_node,\n" +
            "           t.request_method,\n" +
            "           t.api_type,\n" +
            "           t.sql_config,\n" +
            "           t.sort,\n" +
            "           t.create_time,\n" +
            "           t.create_user_id,\n" +
            "           t.update_time,\n" +
            "           t.update_user_id\n" +
            "    FROM api_request t\n" +
            "             INNER JOIN folder_tree ft ON t.parent_id = ft.id\n" +
            "    WHERE t.project_id = ft.project_id\n" +
            "      AND (t.source_drat_id IS NULL OR t.source_drat_id = 0)\n" +
            "      AND t.is_deleted = 0\n" +
            ")\n" +
            "SELECT id,\n" +
            "       parent_id,\n" +
            "       project_id,\n" +
            "       team_id,\n" +
            "       api_name,\n" +
            "       api_node,\n" +
            "       request_method,\n" +
            "       api_type,\n" +
            "       sql_config,\n" +
            "       sort,\n" +
            "       create_time,\n" +
            "       create_user_id,\n" +
            "       update_time,\n" +
            "       update_user_id\n" +
            "FROM folder_tree;")
    List<ApiFolderTreeVO> apiListTree(@Param("projectId") Integer sceneId);


    /**
     * 递归查询指定节点下的所有节点（包含自身、包含所有的source_drat_id）
     */
    @Select("WITH RECURSIVE recursive_data AS (\n" +
            "    -- 锚点查询：起始节点\n" +
            "    SELECT id, parent_id, source_drat_id\n" +
            "    FROM api_request\n" +
            "    WHERE id = #{id}\n" +
            "      AND is_deleted = 0\n" +
            "\n" +
            "    UNION ALL\n" +
            "\n" +
            "    -- 递归查询：查找子节点\n" +
            "    SELECT t.id, t.parent_id, t.source_drat_id\n" +
            "    FROM api_request t\n" +
            "    INNER JOIN recursive_data rd ON (t.parent_id = rd.id OR t.source_drat_id = rd.id)\n" +
            "    WHERE t.is_deleted = 0\n" +
            ")\n" +
            "SELECT DISTINCT id FROM recursive_data;")
    List<ApiRequest> findAllChildrenIds(@Param("id") Integer id);

}
