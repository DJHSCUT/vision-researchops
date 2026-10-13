package com.djh.researchops.rag;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.transformer.splitter.TextSplitter;

/** Spring AI TextSplitter extension: Unicode code points, paragraph/sentence boundaries, exact overlap. */
public final class MarkdownTextSplitter extends TextSplitter {
    private final int size;
    private final int overlap;

    public MarkdownTextSplitter(int size, int overlap) {
        if (size <= 0 || overlap < 0 || overlap >= size / 2) {
            throw new IllegalArgumentException("Invalid chunk size/overlap");
        }
        this.size = size;
        this.overlap = overlap;
    }

    @Override
    protected List<String> splitText(String text) {
        int[] codePoints = text.codePoints().toArray();
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < codePoints.length) {
            int end = Math.min(start + size, codePoints.length);
            if (end < codePoints.length) {
                // Prefer paragraph/newline, then Chinese sentence boundary in the latter half.
                int minimum = start + Math.max(size / 2, overlap + 1);
                int boundary = boundary(codePoints, minimum, end, true);
                if (boundary < 0) boundary = boundary(codePoints, minimum, end, false);
                if (boundary > 0) end = boundary;
            }
            String chunk = new String(codePoints, start, end - start);
            if (!chunk.isBlank()) result.add(chunk);
            if (end == codePoints.length) break;
            start = end - overlap;
        }
        return result;
    }

    private int boundary(int[] text, int minimum, int end, boolean newline) {
        for (int i = end - 1; i >= minimum; i--) {
            if (newline ? text[i] == '\n' : "。！？；".indexOf(text[i]) >= 0) return i + 1;
        }
        return -1;
    }
}
