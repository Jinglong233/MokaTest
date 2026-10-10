package com.mokatest.platform.demos.qa.ai.agent;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.util.Date;

@TableName("ai_chat_session")
@Data
public class AgentSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer projectId;
    private Integer userId;
    private String title;
    /** §7.2 会话绑定标记：首次绑定快捷指令时写入，用于锁定与恢复展示 */
    private String skillScene;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
    @TableLogic
    private Integer isDeleted;
    private Date deletedAt;
}
