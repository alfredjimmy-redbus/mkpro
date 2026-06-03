package org.graphify.graph;

import org.graphify.core.model.Entity;
import org.graphify.core.model.ExtractionResult;
import org.jgrapht.Graph;

public interface GraphBuilder {
    Graph<Entity, RelationshipEdge> buildGraph(ExtractionResult result);
}
