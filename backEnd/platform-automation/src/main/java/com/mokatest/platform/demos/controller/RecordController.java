package com.mokatest.platform.demos.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mokatest.platform.demos.domain.ui.dto.record.RecordImportResultVO;
import com.mokatest.platform.demos.domain.ui.dto.record.RecordSaveDTO;
import com.mokatest.platform.demos.domain.ui.vo.ResponseVO;
import com.mokatest.platform.demos.service.RecordService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 录制导入接口
 */
@RestController
@RequestMapping("/record")
public class RecordController {

    @Resource
    private RecordService recordService;

    /**
     * 上传录制 JSON 文件并解析为草稿步骤（不落库）
     */
    @SaCheckPermission("auto:scene:create")
    @PostMapping("/import")
    public ResponseVO importRecord(@RequestParam("file") MultipartFile file,
                                   @RequestParam("projectId") Integer projectId) {
        RecordImportResultVO result = recordService.importRecord(file, projectId);
        return ResponseVO.success(result);
    }

    /**
     * 保存确认后的草稿步骤，生成 UI 场景
     */
    @SaCheckPermission("auto:scene:create")
    @PostMapping("/save")
    public ResponseVO save(@RequestBody RecordSaveDTO dto) {
        Integer sceneId = recordService.save(dto);
        return ResponseVO.success(sceneId);
    }

}
