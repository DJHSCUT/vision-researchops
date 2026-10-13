package com.djh.researchops.rag;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@Profile("rag")
@ConditionalOnProperty(prefix = "researchops.rag", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RagProperties.class)
public class RagConfiguration {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RagConfiguration.class);
    @Bean
    EmbeddingModel ragEmbeddingModel(RagProperties properties) {
        var factory = new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder()
                .connectTimeout(properties.ollamaTimeout()).build());
        factory.setReadTimeout(properties.ollamaTimeout());
        var api = OllamaApi.builder().baseUrl(properties.ollamaBaseUrl())
                .restClientBuilder(RestClient.builder().requestFactory(factory)).build();
        var model = OllamaEmbeddingModel.builder().ollamaApi(api)
                .options(OllamaEmbeddingOptions.builder().model(RagProperties.MODEL).truncate(false).build())
                .modelManagementOptions(ModelManagementOptions.builder().pullModelStrategy(PullModelStrategy.NEVER).build())
                .build();
        return new FixedDimensionEmbeddingModel(model, properties.collectionName());
    }

    @Bean(destroyMethod = "close")
    QdrantClient ragQdrantClient(RagProperties properties) {
        // These are bound runtime values, including env/CLI overrides; no credentials or request content.
        LOG.info("RAG effective Qdrant config host={} port={} tls={} collection={} timeout={}",
                properties.qdrantHost(), properties.qdrantPort(), properties.qdrantUseTls(),
                properties.collectionName(), properties.qdrantTimeout());
        // io.qdrant client 1.18 defaults to an eager health/version RPC during build().
        // Disable that probe: startup stays offline; collection checks happen on explicit requests.
        return new QdrantClient(QdrantGrpcClient.newBuilder(properties.qdrantHost(), properties.qdrantPort(),
                properties.qdrantUseTls(), false).withTimeout(properties.qdrantTimeout()).build());
    }

    @Bean
    QdrantVectorStore ragVectorStore(QdrantClient ragQdrantClient,
                                     @Qualifier("ragEmbeddingModel") EmbeddingModel embeddingModel,
                                     RagProperties properties) {
        return QdrantVectorStore.builder(ragQdrantClient, embeddingModel)
                .collectionName(properties.collectionName()).initializeSchema(false).build();
    }

    @Bean DemoDocumentLoader demoDocumentLoader(RagProperties properties) { return new DemoDocumentLoader(properties); }

    @Bean
    KnowledgeStore ragKnowledgeStore(QdrantClient ragQdrantClient, QdrantVectorStore ragVectorStore,
                                     RagProperties properties) {
        return new QdrantKnowledgeStore(ragQdrantClient, ragVectorStore, properties);
    }

    @Bean
    RagService ragService(DemoDocumentLoader loader, KnowledgeStore store, RagProperties properties) {
        return new RagService(loader, store, properties);
    }
}
