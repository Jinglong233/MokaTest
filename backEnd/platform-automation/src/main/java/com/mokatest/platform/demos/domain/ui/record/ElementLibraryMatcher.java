package com.mokatest.platform.demos.domain.ui.record;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.domain.ui.Element;
import com.mokatest.platform.demos.domain.ui.dto.record.RecordCandidateDTO;
import com.mokatest.platform.demos.mapper.ElementMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 录制导入时匹配项目元素库
 */
@Component
public class ElementLibraryMatcher {

    @Resource
    private ElementMapper elementMapper;

    /**
     * 根据候选定位查询元素库，命中则返回元素记录
     *
     * @param projectId 项目 ID
     * @param candidate 定位候选
     * @return 命中元素，未命中返回 null
     */
    public Element match(Integer projectId, RecordCandidateDTO candidate) {
        if (projectId == null || candidate == null) {
            return null;
        }
        if (!StringUtils.hasText(candidate.getLocatorType()) || !StringUtils.hasText(candidate.getLocatorValue())) {
            return null;
        }
        QueryWrapper<Element> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId);
        wrapper.eq("locator_type", candidate.getLocatorType());
        wrapper.eq("locator_value", candidate.getLocatorValue());
        wrapper.eq("is_deleted", 0);
        wrapper.last("LIMIT 1");
        List<Element> list = elementMapper.selectList(wrapper);
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

}
