package com.mokatest.platform.demos.domain.ui;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * 导入幂等批次记录表
 *
 * @TableName import_batch_record
 */
@TableName(value = "import_batch_record")
@Data
public class ImportBatchRecord implements Serializable {

    /**
     * 记录ID
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 幂等批次ID（客户端生成的 UUID，全局唯一）
     */
    private String batchId;

    /**
     * 所属项目ID
     */
    private String projectId;

    /**
     * 入口类型：SCENE_IMPORT=场景JSON导入，RECORD_SAVE=录制保存
     */
    private String entryType;

    /**
     * 首次导入结果（场景ID或true）
     */
    private String result;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    private static final long serialVersionUID = 1L;
}
