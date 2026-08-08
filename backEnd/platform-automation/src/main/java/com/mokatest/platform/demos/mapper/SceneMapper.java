package com.mokatest.platform.demos.mapper;

import com.mokatest.platform.demos.domain.ui.Scene;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * @author: JingLong
 * @description 针对表【scene】的数据库操作Mapper
 * @createDate 2025-08-02 11:18:46
 * @Entity com.mokaTest.platform.demos.domain.ui.Scene
 */
@Mapper
public interface SceneMapper extends BaseMapper<Scene> {
    /**
     * 递归查询所有子节点ID
     * MySQL 8.0+ 使用 WITH RECURSIVE
     */
    @Select("WITH RECURSIVE scene_tree AS (" +
            "  SELECT id, parent_id FROM scene WHERE id = #{sceneId} AND is_deleted = 0" +
            "  UNION ALL" +
            "  SELECT s.id, s.parent_id FROM scene s" +
            "  INNER JOIN scene_tree st ON s.parent_id = st.id" +
            "  WHERE s.is_deleted = 0" +
            ") SELECT id FROM scene_tree WHERE id != #{sceneId}")
    List<Integer> findAllChildrenIds(@Param("sceneId") Integer sceneId);

    /**
     * 批量更新排序
     */
    @Update("UPDATE scene SET sort = sort - 1 " +
            "WHERE parent_id = #{parentId} AND sort > #{sort} AND is_deleted = 0")
    void decrementSortAfter(@Param("parentId") Integer parentId,
                            @Param("sort") Integer sort);

    /**
     * 递归查询所有子节点中 SCENE 类型的数量
     */
    @Select("WITH RECURSIVE scene_tree AS (" +
            "  SELECT id, scene_type FROM scene WHERE id = #{sceneId} AND is_deleted = 0" +
            "  UNION ALL" +
            "  SELECT s.id, s.scene_type FROM scene s" +
            "  INNER JOIN scene_tree st ON s.parent_id = st.id" +
            "  WHERE s.is_deleted = 0" +
            ") SELECT COUNT(*) FROM scene_tree WHERE scene_type = 'SCENE'")
    Integer countSceneChildren(@Param("sceneId") Integer sceneId);


}




