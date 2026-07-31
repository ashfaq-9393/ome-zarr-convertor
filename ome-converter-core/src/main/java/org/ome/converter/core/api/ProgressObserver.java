package org.ome.converter.core.api;

public interface ProgressObserver {
    void onProgress(double percentage, long tilesProcessed, long totalTiles, String currentTask);
    
    default void onProgress(double percentage, String currentTask) {
        onProgress(percentage, (long) percentage, 100, currentTask);
    }
    
    void onLog(String level, String message);
    
    boolean isCancelled();
}
