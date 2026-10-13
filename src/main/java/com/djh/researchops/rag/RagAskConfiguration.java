package com.djh.researchops.rag;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("ai & rag & !prod & !production")
@ConditionalOnProperty(prefix = "researchops.rag", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "researchops.rag.ask", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RagAskProperties.class)
public class RagAskConfiguration {
    @Bean RagPromptContextBuilder ragPromptContextBuilder(RagAskProperties properties, RagProperties ragProperties) {
        return new RagPromptContextBuilder(properties, ragProperties);
    }
    @Bean RagCitationValidator ragCitationValidator() { return new RagCitationValidator(); }
    @Bean RagAskService ragAskService(RagService retrieval, ChatModel chatModel, RagPromptContextBuilder context,
                                     RagCitationValidator citations, RagAskProperties properties, RagProperties ragProperties) {
        return new RagAskService(retrieval, chatModel, context, citations, properties, ragProperties.collectionName());
    }
}
