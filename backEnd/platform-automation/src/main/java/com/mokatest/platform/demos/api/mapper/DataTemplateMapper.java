package com.mokatest.platform.demos.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mokatest.platform.demos.api.domain.DataTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 数据模板 Mapper
 *
 * @author JingLong
 * @since 2026-06-17
 */
@Mapper
public interface DataTemplateMapper extends BaseMapper<DataTemplate> {

    /**
     * CTE 递归查询项目下所有 FOLDER 节点（用于文件夹下拉选择）
     */
    List<DataTemplate> folderList(@Param("projectId") Integer projectId);

    /**
     * CTE 递归查询项目下全部节点（FOLDER + TEMPLATE）
     */
    List<DataTemplate> treeList(@Param("projectId") Integer projectId);

    /**
     * CTE 递归查询指定节点的所有后代 ID（含自身）
     */
    List<Integer> findAllChildrenIds(@Param("id") Integer id);

    /**
     * 删除后，将同父节点下 sort 大于指定值的节点 sort 减 1
     */
    int decrementSortAfter(@Param("parentId") Integer parentId, @Param("sort") Integer sort);
}
