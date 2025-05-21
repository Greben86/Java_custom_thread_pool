package mephi.exercise.pool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Кастомная реализация пула потоков
 */
@Slf4j
public class CustomExecutorImpl implements CustomExecutor {

    private final int corePoolSize;
    private final int maxPoolSize;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private final int queueSize;
    private final int minSpareThreads;
    private final ThreadFactory threadFactory = new CustomThreadFactory();
    private final RejectedExecutionHandler rejectionHandler = new CustomRejectionHandler();

    private final ReentrantLock mainLock = new ReentrantLock();
    private final AtomicInteger activeThreads = new AtomicInteger(0);
    private final AtomicInteger currentPoolSize = new AtomicInteger(0);
    private final AtomicBoolean shutdownFlag = new AtomicBoolean(false);
    private Map<Integer, Worker> workers;
    private Map<Integer, BlockingQueue<Runnable>> queues;
    private LoadBalancer balancer;

    public CustomExecutorImpl(int corePoolSize, int maxPoolSize, long keepAliveTime, TimeUnit timeUnit, int queueSize,
                              int minSpareThreads) {
        if (corePoolSize < 0 || maxPoolSize <= 0 || maxPoolSize < corePoolSize ||
                keepAliveTime < 0 || queueSize <= 0 || minSpareThreads < 0) {
            throw new IllegalArgumentException("Invalid thread pool parameters");
        }

        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.queueSize = queueSize;
        this.minSpareThreads = minSpareThreads;

        init();
    }

    private void init() {
        mainLock.lock();
        try {
            workers = new HashMap<>(maxPoolSize);
            queues = new HashMap<>(maxPoolSize);
            balancer = new LoadBalancer(workers);
            for (int i = 0; i < corePoolSize; i++) {
                startNewWorker();
            }
        } finally {
            mainLock.unlock();
        }
    }

    private int startNewWorker() {
        final int id = currentPoolSize.incrementAndGet();
        Worker worker = new Worker(id, this::nextTask, this::statusForIdleWorker);
        workers.put(id, worker);
        queues.put(id, new ArrayBlockingQueue<>(queueSize));
        threadFactory.newThread(worker).start();
        return id;
    }

    private Runnable nextTask(final int id) throws InterruptedException {
        BlockingQueue<Runnable> queue;
        if ((queue = queues.get(id)) != null) {
            return queue.poll(keepAliveTime, timeUnit);
        }
        return null;
    }

    private Status statusForIdleWorker(final int id) {
        if (shutdownFlag.get()) {
            return Status.STOPPED;
        }

        mainLock.lock();
        try {
            // Ищем ожидающих
            final var count = workers.values().stream()
                    .filter(worker -> Status.WAITING.equals(worker.getStatus()))
                    .count();

            log.info("Count of waiting workers = {}, min of waiting workers = {}", count, minSpareThreads);
            if (count > minSpareThreads) {
                // Этот воркер будет остановлен - удаляем его из списка
                workers.remove(id);
                currentPoolSize.decrementAndGet();
                return Status.STOPPED;
            }

            return Status.WAITING;
        } finally {
            mainLock.unlock();
        }
    }

    @Override
    public void execute(Runnable command) {
        if (command == null) {
            throw new NullPointerException("Task cannot be null");
        }
        if (shutdownFlag.get()) {
            return;
        }

        mainLock.lock();
        try {
            final int activeCount = activeThreads.get();
            final int currentSize = currentPoolSize.get();

            // Если
            final int id;
            if (activeCount >= currentSize && currentSize < maxPoolSize) {
                id = startNewWorker();
                log.info("Created new worker thread. Current pool size: {}", currentPoolSize.get());
            } else {
                id = balancer.getNextThreadId();
            }

            final var targetQueue = queues.get(id);
            final var task = new TaskWrapper(command);
            if (!targetQueue.offer(task)) {
                rejectionHandler.rejectedExecution(task, null);
            } else {
                log.warn("Task submitted to queue {}", id);
            }
        } catch (RejectedExecutionException e) {
            log.error("Task rejected", e);
        } finally {
            mainLock.unlock();
        }
    }

    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        if (callable == null) {
            throw new NullPointerException("Task cannot be null");
        }
        if (shutdownFlag.get()) {
            return null;
        }

        final var result = new FutureTask<>(callable);
        execute(result);
        return result;
    }

    @Override
    public void shutdown() {
        shutdownFlag.getAndSet(true);
        log.info("shutdown = {}", shutdownFlag.get());
    }

    @Override
    public void shutdownNow() {
        mainLock.lock();
        try {
            shutdown();
            workers.forEach((id, worker) -> worker.stop());
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Класс-баллансер для распределения нагрузки между потоками
     */
    @RequiredArgsConstructor
    public static class LoadBalancer {
        private final Map<Integer, Worker> threads;
        private int currentIndex = 0;

        public int getNextThreadId() {
            // Сперва ищем свободные
            final var waitingWorkerId = threads.entrySet().stream()
                    .filter(entry -> Status.WAITING.equals(entry.getValue().getStatus()))
                    .map(Map.Entry::getKey)
                    .findAny();
            if (waitingWorkerId.isPresent()) {
                return waitingWorkerId.get();
            }

            // Если свободных нет, то используем принцип round robin
            currentIndex = Math.min(currentIndex, threads.size() - 1);
            int index = 0;
            for (Map.Entry<Integer, Worker> entry : threads.entrySet()) {
                // Выбираем случайный воркер
                if (index == currentIndex) {
                    currentIndex = (currentIndex + 1) % threads.size();
                    return entry.getKey();
                }
                index++;
            }

            throw new IllegalStateException();
        }
    }

    /**
     * Класс-обертка, добавляет функциональность изменения количества активных потоков
     */
    @RequiredArgsConstructor
    private class TaskWrapper implements Runnable {

        private final Runnable task;

        @Override
        public void run() {
            activeThreads.incrementAndGet();
            try {
                task.run();
            } finally {
                activeThreads.decrementAndGet();
            }
        }
    }

    /**
     * Обработчик отказа выполнения задач
     */
    private static class CustomRejectionHandler implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            log.warn("Task rejected: {}", r.toString());
            throw new RejectedExecutionException("Task rejected: " + r);
        }
    }
}
