package org.graphify.analysis;

import org.graphify.core.model.Entity;
import org.graphify.graph.RelationshipEdge;
import org.jgrapht.Graph;

public interface GraphAnalyzer {
    AnalysisResult analyze(Graph<Entity, RelationshipEdge> graph);
}
