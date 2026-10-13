package com.djh.researchops.rag;

import java.util.List;

public record RagAskResponse(String answer, List<Source> sources, boolean grounded, boolean insufficientEvidence,
                             int retrievedCount, List<Source> retrievedSources, String evidenceStatus) {
    public record Source(int citationId, String title, String source, int chunkIndex, double score,
                         boolean isDemo, boolean usedInContext) { }
}
