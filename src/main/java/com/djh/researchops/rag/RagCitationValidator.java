package com.djh.researchops.rag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Checks citation targets and syntax only, not the factual support of natural-language sentences. */
public final class RagCitationValidator {
    public Validation validate(String answer, List<RagAskResponse.Source> contextSources) {
        var available = contextSources.stream().collect(java.util.stream.Collectors.toMap(RagAskResponse.Source::citationId, s -> s));
        var used = new LinkedHashSet<Integer>();
        for (int i = 0; i < answer.length(); i++) {
            char ch = answer.charAt(i);
            if (ch == ']' || ch == '【' || ch == '】' || ch == '［' || ch == '］') return new Validation(false, List.of());
            if (ch != '[') continue;
            int end = answer.indexOf(']', i + 1);
            if (end < 0) return new Validation(false, List.of());
            String token = answer.substring(i + 1, end);
            if (!token.matches("[1-9][0-9]{0,2}")) return new Validation(false, List.of());
            int id = Integer.parseInt(token);
            if (!available.containsKey(id)) return new Validation(false, List.of());
            used.add(id);
            i = end;
        }
        var cited = new ArrayList<RagAskResponse.Source>();
        used.forEach(id -> cited.add(available.get(id)));
        return new Validation(!cited.isEmpty(), List.copyOf(cited));
    }
    public record Validation(boolean valid, List<RagAskResponse.Source> citedSources) { }
}
