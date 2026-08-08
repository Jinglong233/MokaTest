package com.mokatest.platform.demos.service;

import com.mokatest.platform.demos.domain.ui.dto.record.RecordImportResultVO;
import com.mokatest.platform.demos.domain.ui.dto.record.RecordSaveDTO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 录制导入服务
 */
public interface RecordService {

    /**
     * 上传并解析录制文件，返回草稿步骤（不落库）
     *
     * @param file      JSON 文件
     * @param projectId 项目 ID
     * @return 转换结果
     */
    RecordImportResultVO importRecord(MultipartFile file, Integer projectId);

    /**
     * 保存确认后的步骤，生成 UI 场景
     *
     * @param dto 保存入参
     * @return 新建场景 ID
     */
    Integer save(RecordSaveDTO dto);

}
