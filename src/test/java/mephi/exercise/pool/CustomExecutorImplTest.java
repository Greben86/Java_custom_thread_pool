package mephi.exercise.pool;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
class CustomExecutorImplTest {

    private final AtomicInteger taskCounter = new AtomicInteger(0);

    @Test
    void execute() throws InterruptedException {
        final var pool = new CustomExecutorImpl(
                2,  // corePoolSize
                10,  // maxPoolSize
                1L,  // keepAliveTime
                TimeUnit.SECONDS,
                10,  // queueSize
                2   // minSpareThreads
        );

        taskCounter.getAndSet(0);
        for (int i = 0; i < 50; i++) {
            pool.execute(this::exampleTask);
        }
        pool.shutdown();

        TimeUnit.SECONDS.sleep(8);

        Assertions.assertEquals(50, taskCounter.get());
    }

    @Test
    void submit() throws ExecutionException, InterruptedException {
        final var pool = new CustomExecutorImpl(
                2,  // corePoolSize
                10,  // maxPoolSize
                1L,  // keepAliveTime
                TimeUnit.SECONDS,
                10,  // queueSize
                2   // minSpareThreads
        );

        pool.submit(() -> "123");
        taskCounter.getAndSet(0);
        var list = new ArrayList<Future<String>>();
        for (int i = 0; i < 50; i++) {
            list.add(pool.submit(this::exampleFutureTask));
        }
        pool.shutdown();

        for (Future<String> future : list) {
            log.info(future.get());
        }

        Assertions.assertEquals(50, taskCounter.get());
    }

    @Test
    void shutdownNow() throws InterruptedException {
        final var pool = new CustomExecutorImpl(
                2,  // corePoolSize
                10,  // maxPoolSize
                1L,  // keepAliveTime
                TimeUnit.SECONDS,
                10,  // queueSize
                2   // minSpareThreads
        );

        taskCounter.getAndSet(0);
        for (int i = 0; i < 50; i++) {
            pool.execute(this::exampleTask);
        }
        pool.shutdownNow();

        TimeUnit.SECONDS.sleep(5);

        Assertions.assertTrue(taskCounter.get() > 0);
        Assertions.assertTrue(taskCounter.get() < 50);
    }

    private void exampleTask() {
        try {
            log.info("Start example runnable task");
            TimeUnit.MILLISECONDS.sleep(120);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            taskCounter.incrementAndGet();
        }
    }

    private String exampleFutureTask() {
        try {
            log.info("Start example future task");
            TimeUnit.MILLISECONDS.sleep(100);
            return "success future task";
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            taskCounter.incrementAndGet();
        }
    }
}