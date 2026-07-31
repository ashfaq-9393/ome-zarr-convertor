package org.ome.converter.core.engine;

import org.ome.converter.core.api.ProgressObserver;
import org.ome.converter.core.writer.OmeZarrWriterStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class TileProducerConsumerEngine {

    private static final Logger log = LoggerFactory.getLogger(TileProducerConsumerEngine.class);
    
    private final ArrayBlockingQueue<TileChunk> tileQueue;
    private final ExecutorService workerPool;
    private final int threadCount;
    private final OmeZarrWriterStrategy writerStrategy;
    private final AtomicLong totalBytesProcessed = new AtomicLong(0);
    private final AtomicLong chunksProcessed = new AtomicLong(0);
    private long totalChunksExpected = 1;
    private long startTime;

    public TileProducerConsumerEngine(int threadCount, int queueCapacity, OmeZarrWriterStrategy writerStrategy) {
        this.threadCount = Math.max(1, threadCount);
        this.tileQueue = new ArrayBlockingQueue<>(Math.max(10, queueCapacity));
        this.writerStrategy = writerStrategy;
        this.workerPool = Executors.newFixedThreadPool(this.threadCount);
    }

    public ArrayBlockingQueue<TileChunk> getTileQueue() {
        return tileQueue;
    }

    public void startProcessing(File levelDir, long totalChunks, ProgressObserver observer) {
        this.totalChunksExpected = Math.max(1, totalChunks);
        this.startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            workerPool.submit(() -> {
                try {
                    while (true) {
                        TileChunk chunk = tileQueue.take();
                        if (chunk.isPoisonPill()) {
                            tileQueue.put(chunk);
                            break;
                        }

                        writerStrategy.writeChunk(levelDir, chunk.getChannel(), chunk.getZIndex(), chunk.getYIndex(), chunk.getXIndex(), chunk.getPixelData());
                        
                        long done = chunksProcessed.incrementAndGet();
                        long bytes = totalBytesProcessed.addAndGet(chunk.getByteSize());

                        if (observer != null && (done % 5 == 0 || done == totalChunksExpected)) {
                            double progress = Math.min(1.0, (double) done / totalChunksExpected);
                            long elapsedMs = Math.max(1, System.currentTimeMillis() - startTime);
                            double mbPerSec = (bytes / (1024.0 * 1024.0)) / (elapsedMs / 1000.0);
                            String status = String.format("Processed %d/%d tiles (%.2f MB/s)", done, totalChunksExpected, mbPerSec);
                            observer.onProgress(progress * 100.0, done, totalChunksExpected, status);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (IOException e) {
                    log.error("Failed to write tile chunk", e);
                }
            });
        }
    }

    public void enqueueChunk(TileChunk chunk) throws InterruptedException {
        tileQueue.put(chunk);
    }

    public void finishProcessing() {
        try {
            tileQueue.put(TileChunk.poisonPill());
            workerPool.shutdown();
            if (!workerPool.awaitTermination(1, TimeUnit.HOURS)) {
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            workerPool.shutdownNow();
        }
    }

    public long getTotalBytesProcessed() {
        return totalBytesProcessed.get();
    }

    public double getThroughputMbPerSec() {
        long elapsedMs = Math.max(1, System.currentTimeMillis() - startTime);
        return (totalBytesProcessed.get() / (1024.0 * 1024.0)) / (elapsedMs / 1000.0);
    }
}
