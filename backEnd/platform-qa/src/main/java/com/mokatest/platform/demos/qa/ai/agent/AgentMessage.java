package com.mokatest.platform.demos.qa.ai.agent;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.util.Date;

@TableName("ai_chat_message")
@Data
public class AgentMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    /** user / assistant / tool */
    private String role;
    private String content;
    private String toolName;
    private String toolResult;
    /** 快捷指令场景标识（仅 role=user 时有值） */
    private String skillScene;
    /** 失败原因（仅 role=assistant 且生成失败时有值） */
    private String errorMsg;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
