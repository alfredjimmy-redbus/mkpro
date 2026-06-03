package org.graphify.core.model;

import java.util.List;

public record ExtractionResult(
    List<Entity> entities,
    List<Relationship> relationships
) {}
