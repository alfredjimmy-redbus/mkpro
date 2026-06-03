package org.graphify.parser;

import org.graphify.core.model.ExtractionResult;
import java.nio.file.Path;

public interface SourceExtractor {
    ExtractionResult extract(Path sourcePath);
}
