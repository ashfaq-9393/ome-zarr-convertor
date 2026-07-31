package org.ome.converter.core.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncEventBus {
    private static final Logger log = LoggerFactory.getLogger(AsyncEventBus.class);
    private static final AsyncEventBus INSTANCE = new AsyncEventBus();

    private final List<EventListener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "EventBus-Worker");
        t.setDaemon(true);
        return t;
    });

    private AsyncEventBus() {}

    public static AsyncEventBus getInstance() {
        return INSTANCE;
    }

    public void register(EventListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void unregister(EventListener listener) {
        listeners.remove(listener);
    }

    public void publishProgress(ProgressEvent event) {
        executor.submit(() -> {
            for (EventListener listener : listeners) {
                try {
                    listener.onProgress(event);
                } catch (Exception e) {
                    log.error("Error dispatching ProgressEvent to listener", e);
                }
            }
        });
    }

    public void publishLog(LogEvent event) {
        executor.submit(() -> {
            for (EventListener listener : listeners) {
                try {
                    listener.onLog(event);
                } catch (Exception e) {
                    log.error("Error dispatching LogEvent to listener", e);
                }
            }
        });
    }

    public void publishGapAnalysis(GapAnalysisEvent event) {
        executor.submit(() -> {
            for (EventListener listener : listeners) {
                try {
                    listener.onGapAnalysis(event);
                } catch (Exception e) {
                    log.error("Error dispatching GapAnalysisEvent to listener", e);
                }
            }
        });
    }
}
