package com.djh.researchops.vo;

import java.time.LocalDateTime;

public record LogToolItem(String level, String content, LocalDateTime createdAt) {
    public static LogToolItem from(ExperimentLogVO log) {
        return new LogToolItem(log.getLevel(), log.getContent(), log.getCreatedAt());
    }
}
