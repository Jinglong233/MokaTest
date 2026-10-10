package com.mokatest.platform.demos.ai.rag;

import com.mokatest.platform.demos.ai.config.ModelManager;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * RAG 配置：用 LangChain4j 原生 QdrantEmbeddingStore 替代手写 311 行 REST。
 *
 * collection 按项目隔离（kb_{projectId}），首次使用时按向量模型维度自动创建。
 */
@Configuration
@ConditionalOnProperty(name = "mokatest.qdrant.enabled", havingValue = "true")
public class RagConfig {

    private static final Logger log = LoggerFactory.getLogger(RagConfig.class);

    @Value("${mokatest.qdrant.host:127.0.0.1}")
    private String host;

    @Value("${mokatest.qdrant.port:6334}")
    private int port;

    @Value("${mokatest.qdrant.api-key:}")
    private String apiKey;

    /** gRPC 是否启用 TLS（qdrant-client 默认 true，本地 Qdrant 默认不开 TLS，必须显式 false） */
    @Value("${mokatest.qdrant.use-tls:false}")
    private boolean useTls;

    @Autowired
    private ModelManager modelManager;

    /** 已确认存在的 collection（避免每次建 store 都查一次 Qdrant） */
    private final Set<String> ensuredCollections = ConcurrentHashMap.newKeySet();

    /**
     * 创建项目级 QdrantEmbeddingStore（collection 不存在时自动创建）。
     *
     * QdrantEmbeddingStore 不会自动建集合（缺失时抛异常），这里先用 QdrantClient
     * 探测并按向量模型实际维度创建（COSINE 距离）。
     */
    public EmbeddingStore<TextSegment> createEmbeddingStore(String collectionName) {
        ensureCollection(collectionName);
        return QdrantEmbeddingStore.builder()
                .host(host)
                .port(port)
                .useTls(useTls)
                .apiKey(apiKey.isEmpty() ? null : apiKey)
                .collectionName(collectionName)
                .build();
    }

    /** collection 不存在则创建；维度通过向量模型探测一次获得 */
    private void ensureCollection(String collectionName) {
        if (ensuredCollections.contains(collectionName)) {
            return;
        }
        synchronized (this) {
            if (ensuredCollections.contains(collectionName)) {
                return;
            }
            QdrantGrpcClient.Builder grpcBuilder =
                    QdrantGrpcClient.newBuilder(host, port, useTls);
            if (!apiKey.isEmpty()) {
                grpcBuilder.withApiKey(apiKey);
            }
            try (QdrantClient client = new QdrantClient(grpcBuilder.build())) {
                if (client.listCollectionsAsync().get().contains(collectionName)) {
                    ensuredCollections.add(collectionName);
                    return;
                }
                // 维度探测：embed 一个短文本取向量长度，避免硬编码各模型维度
                int dimension = modelManager.getEmbeddingModel()
                        .embed("dimension probe").content().vector().length;
                client.createCollectionAsync(collectionName, VectorParams.newBuilder()
                        .setSize(dimension)
                        .setDistance(Distance.Cosine)
                        .build()).get();
                ensuredCollections.add(collectionName);
                log.info("Qdrant collection 已创建: {} (dimension={})", collectionName, dimension);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Qdrant collection 创建被中断: " + collectionName, e);
            } catch (ExecutionException e) {
                throw new IllegalStateException("Qdrant collection 创建失败: " + collectionName
                        + " | " + e.getCause().getMessage(), e);
            }
        }
    }

    public EmbeddingModel getEmbeddingModel() {
        return modelManager.getEmbeddingModel();
    }
}
