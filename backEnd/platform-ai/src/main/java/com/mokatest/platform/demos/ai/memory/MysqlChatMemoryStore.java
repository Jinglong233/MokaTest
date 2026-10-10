package com.mokatest.platform.demos.ai.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * LangChain4j ChatMemoryStore 的 MySQL 持久化实现。
 *
 * 替代旧 ConversationHistory 手写 JSON 轮次摘要 —
 * 框架自动管理完整 ChatMessage 链（含淘汰策略），持久化到 ai_chat_memory 表。
 */
@Component
public class MysqlChatMemoryStore implements ChatMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(MysqlChatMemoryStore.class);

    @Autowired
    private AiChatMemoryMapper mapper;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        AiChatMemory row = mapper.selectById(memoryId.toString());
        if (row == null || row.getMessages() == null || row.getMessages().isEmpty()) {
            return List.of();
        }
        try {
            return ChatMessageDeserializer.messagesFromJson(row.getMessages());
        } catch (Exception e) {
            log.warn("ChatMemory 反序列化失败，重置为空: memoryId={}, err={}", memoryId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String json = ChatMessageSerializer.messagesToJson(messages);
        AiChatMemory row = mapper.selectById(memoryId.toString());
        if (row == null) {
            row = new AiChatMemory();
            row.setMemoryId(memoryId.toString());
            row.setMessages(json);
            row.setCreateTime(new Date());
            row.setUpdateTime(new Date());
            mapper.insert(row);
        } else {
            row.setMessages(json);
            row.setUpdateTime(new Date());
            mapper.updateById(row);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        mapper.deleteById(memoryId.toString());
    }
}
