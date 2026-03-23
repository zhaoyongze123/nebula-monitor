package com.nebula.server.diagnosis;

import com.nebula.common.MonitoringData;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DiagnosisTaskExecutor - Manages the thread pool for asynchronous diagnosis tasks
 * Provides singleton executor service with configurable pool size
 */
public class DiagnosisTaskExecutor {
    private static final Logger logger = Logger.getLogger(DiagnosisTaskExecutor.class.getName());
    private static final DiagnosisConfig config = DiagnosisConfig.getInstance();
    private static volatile boolean initialized = false;
    private static ExecutorService executor;
    private static final Object INIT_LOCK = new Object();

    /**
     * Initialize the diagnosis thread pool
     * Should be called once during application startup
     */
    public static synchronized void initialize() {
        if (initialized) {
            logger.info("DiagnosisTaskExecutor already initialized");
            return;
        }

        synchronized (INIT_LOCK) {
            if (initialized) {
                return;
            }

            try {
                int corePoolSize = config.getThreadPoolCoreSize();
                int maxPoolSize = config.getThreadPoolMaxSize();
                int queueCapacity = config.getThreadPoolQueueCapacity();

                BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(queueCapacity);

                executor = new ThreadPoolExecutor(
                        corePoolSize,
                        maxPoolSize,
                        300, // Keep alive time: 5 minutes
                        TimeUnit.SECONDS,
                        queue,
                        new ThreadFactory() {
                            private final ThreadGroup group = Thread.currentThread().getThreadGroup();
                            private final AtomicInteger threadNumber = new AtomicInteger(1);
                            private static final String NAME_PREFIX = "nebula-diagnosis-";

                            @Override
                            public Thread newThread(Runnable r) {
                                Thread t = new Thread(group, r,
                                        NAME_PREFIX + threadNumber.getAndIncrement(),
                                        0);
                                if (t.isDaemon())
                                    t.setDaemon(false);
                                if (t.getPriority() != Thread.NORM_PRIORITY)
                                    t.setPriority(Thread.NORM_PRIORITY);
                                return t;
                            }
                        },
                        new ThreadPoolExecutor.DiscardOldestPolicy() {
                            @Override
                            public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                                logger.warning("Diagnosis task queue full, discarding oldest task");
                                super.rejectedExecution(r, e);
                            }
                        }
                );

                initialized = true;
                logger.info("DiagnosisTaskExecutor initialized: coreSize=" + corePoolSize +
                        ", maxSize=" + maxPoolSize + ", queueCapacity=" + queueCapacity);

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to initialize DiagnosisTaskExecutor", e);
                throw new RuntimeException("Failed to initialize diagnosis executor", e);
            }
        }
    }

    /**
     * Submit a diagnosis task for asynchronous processing
     * @param data MonitoringData to diagnose
     * @return Future for tracking task completion
     */
    public static Future<?> submitDiagnosisTask(MonitoringData data) {
        if (executor == null) {
            logger.warning("DiagnosisTaskExecutor not initialized, cannot submit task");
            return null;
        }

        if (!SlowTraceDetector.shouldDiagnose(data)) {
            return null;
        }

        try {
            DiagnosisTask task = new DiagnosisTask(data);
            return executor.submit(task);
        } catch (RejectedExecutionException e) {
            logger.log(Level.WARNING, "Failed to submit diagnosis task, queue might be full", e);
            return null;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error submitting diagnosis task", e);
            return null;
        }
    }

    /**
     * Get the current executor service
     */
    public static ExecutorService getExecutor() {
        if (executor == null) {
            initialize();
        }
        return executor;
    }

    /**
     * Get thread pool statistics
     */
    public static String getPoolStatistics() {
        if (!(executor instanceof ThreadPoolExecutor)) {
            return "Executor is not a ThreadPoolExecutor";
        }

        ThreadPoolExecutor tpe = (ThreadPoolExecutor) executor;
        return String.format(
                "CorePoolSize=%d, MaxPoolSize=%d, ActiveCount=%d, TaskCount=%d, CompletedTaskCount=%d",
                tpe.getCorePoolSize(),
                tpe.getMaximumPoolSize(),
                tpe.getActiveCount(),
                tpe.getTaskCount(),
                tpe.getCompletedTaskCount()
        );
    }

    /**
     * Shutdown the executor gracefully
     */
    public static void shutdown() {
        if (executor == null) {
            return;
        }

        try {
            logger.info("Shutting down DiagnosisTaskExecutor...");
            executor.shutdown();

            // Wait up to 30 seconds for existing tasks to terminate
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warning("Executor did not terminate in time, forcing shutdown");
                executor.shutdownNow();
            }

            logger.info("DiagnosisTaskExecutor shutdown complete");
        } catch (InterruptedException e) {
            logger.log(Level.WARNING, "Interrupted during executor shutdown", e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            executor = null;
            initialized = false;
        }
    }

    /**
     * Simple wrapper class to hold AtomicInteger for thread numbering
     */
    private static class AtomicInteger {
        private int value;

        AtomicInteger(int initialValue) {
            this.value = initialValue;
        }

        synchronized int getAndIncrement() {
            return value++;
        }
    }
}
