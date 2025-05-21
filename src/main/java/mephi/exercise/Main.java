package mephi.exercise;

import lombok.extern.slf4j.Slf4j;
import mephi.exercise.pool.CustomExecutorImpl;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Демонстрационная программа
 */
@Slf4j
public class Main {
    private static final AtomicInteger completedTasks = new AtomicInteger(0);
    private static final AtomicInteger rejectedTasks = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        // Create thread pool with parameters
        final var pool = new CustomExecutorImpl(
                2,  // corePoolSize
                4,  // maxPoolSize
                5L,  // keepAliveTime
                TimeUnit.SECONDS,
                5,  // queueSize
                1   // minSpareThreads
        );

        log.info("Starting thread pool demonstration...");

        // Normal operation with moderate load
        log.info("Normal operation with moderate load");
        executeTasks(pool, 10, 1000);
        waitForTasks(15);

        // High load with potential rejections
        log.info("High load with potential rejections");
        executeTasks(pool, 20, 500);
        waitForTasks(15);

        // Burst of tasks
        log.info("Burst of tasks");
        executeTasks(pool, 30, 200);
        waitForTasks(15);

        // Long-running tasks
        log.info("Long-running tasks");
        executeTasks(pool, 5, 5000);
        waitForTasks(10);

        // Shutdown the pool
        log.info("Pool shutdown...");
        pool.shutdown();
        waitForTasks(10);

        // Print final statistics
        log.info("Statistics:");
        log.info("Completed tasks: {}", completedTasks.get());
        log.info("Rejected tasks: {}", rejectedTasks.get());
    }

    private static void executeTasks(CustomExecutorImpl pool, int count, int sleepTime) {
        for (int i = 0; i < count; i++) {
            final int taskId = i;
            try {
                pool.execute(() -> {
                    log.info("Task {} started", taskId);
                    try {
                        TimeUnit.MILLISECONDS.sleep(sleepTime);
                        completedTasks.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    log.info("Task {} completed", taskId);
                });
            } catch (Exception e) {
                rejectedTasks.incrementAndGet();
                log.warn("Task {} was rejected", taskId);
            }
        }
    }

    private static void waitForTasks(int seconds) throws InterruptedException {
        log.info("Waiting for {} seconds...", seconds);
        TimeUnit.SECONDS.sleep(seconds);
    }
}