package com.mokatest.platform.demos.qa.ai.agent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mokatest.platform.demos.ai.config.ModelManager;
import com.mokatest.platform.demos.ai.rag.KnowledgeIngestService;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SearchKnowledgeTool implements AgentTool {

    @Autowired(required = false)
    private KnowledgeIngestService ingestService;

    @Autowired(required = false)
    private ModelManager modelManager;

    @Override public String name() { return "search_knowledge"; }
    @Override public String description() { return "搜索项目知识库，返回相关文档片段（标题+内容摘要）"; }
    @Override public String paramSchema() { return "{\"query\": \"搜索内容（必填）\"}"; }
    @Override public String requiredPermission() { return "knowledge:view"; }

    @Override
    public String execute(AgentContext ctx, JSONObject args) {
        if (ingestService == null || modelManager == null) {
            return "{\"error\": \"知识库未启用\"}";
        }
        String query = args != null && args.containsKey("query") ? args.getString("query") : null;
        if (query == null || query.isBlank()) {
            return "{\"error\": \"query 必填\"}";
        }
        EmbeddingStore<TextSegment> store = ingestService.getEmbeddingStore(ctx.getProjectId());
        if (store == null) {
            return "{\"error\": \"知识库未启用\"}";
        }
        ContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(modelManager.getEmbeddingModel(ctx.getProjectId()))
                .maxResults(5)
                .minScore(0.5)
                .build();
        List<Content> contents = retriever.retrieve(Query.from(query));
        JSONArray result = new JSONArray();
        for (Content c : contents) {
            JSONObject obj = new JSONObject();
            obj.put("title", c.textSegment().metadata().getString("docTitle"));
            String text = c.textSegment().text();
            obj.put("snippet", text != null && text.length() > 200 ? text.substring(0, 200) + "..." : text);
            result.add(obj);
        }
        return result.toJSONString();
    }

    private String abbreviate(String text, int max) {
        if (text == null || text.length() <= max) return text;
        return text.substring(0, max) + "...";
    }
}
