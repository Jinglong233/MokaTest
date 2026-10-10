package com.mokatest.platform.demos.qa.aireview;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * AI 需求评审记录（项目级，保留历史）
 */
@TableName("ai_requirement_review")
@Data
public class AiRequirementReview {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer projectId;

    /** 关联需求ID */
    private Long requirementId;

    /** 六维评分JSON */
    private String scoresJson;

    private Integer overallScore;

    /** 风险列表JSON */
    private String risksJson;

    /** 检查表作答明细 */
    private String checklistJson;

    /** 内容哈希（幂等复用键） */
    private String contentHash;

    /** 置信度 HIGH/MEDIUM/LOW */
    private String confidence;

    /** SUCCESS/FAILED/STOPPED/RUNNING */
    private String status;

    private String summary;

    /** 模型原始输出（排障用） */
    private String rawText;

    /** 专家级状态机+结论 JSON {completeness:{status:PENDING/RUNNING/DONE/FAILED,result:...},...} */
    private String expertResults;

    /** 用户补充评审指令 */
    private String instruction;

    private String modelName;

    private Integer tokens;

    private String createUserId;

    private Date createTime;

    @TableLogic
    private Integer isDeleted;

    private Date deletedAt;
}
