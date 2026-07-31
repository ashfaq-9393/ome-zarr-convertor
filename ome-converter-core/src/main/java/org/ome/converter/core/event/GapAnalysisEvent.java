package org.ome.converter.core.event;

import org.ome.converter.core.model.GapAnalysisResult;

public record GapAnalysisEvent(
    String jobId,
    GapAnalysisResult result
) {}
