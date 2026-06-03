package org.graphify.core.model;

import java.util.Map;

public record Relationship(
    String id,
    String sourceId,
    String targetId,
    RelType type,
    Map<String, Object> metadata
) {}
