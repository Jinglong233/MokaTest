package com.mokatest.platform.demos.ai.memory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * LangChain4j ChatMemory 持久化实体
 * @TableName ai_chat_memory
 */
@TableName("ai_chat_memory")
@Data
public class AiChatMemory {

    @TableId(type = IdType.INPUT)
    private String memoryId;

    /** ChatMessage JSON（ChatMessageSerializer.messagesToJson） */
    private String messages;

    private Date createTime;

    private Date updateTime;
}
