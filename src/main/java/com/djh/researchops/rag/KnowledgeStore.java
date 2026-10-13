package com.djh.researchops.rag;

import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;

public interface KnowledgeStore {
    void ensureCollection(boolean createIfMissing);
    Map<String, String> existingChunks(String documentId);
    void add(List<Document> chunks);
    List<Document> search(SearchRequest request);
}
