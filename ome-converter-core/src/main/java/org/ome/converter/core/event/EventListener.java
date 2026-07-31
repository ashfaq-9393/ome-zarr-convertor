package org.ome.converter.core.event;

public interface EventListener {
    default void onProgress(ProgressEvent event) {}
    default void onLog(LogEvent event) {}
    default void onGapAnalysis(GapAnalysisEvent event) {}
}
