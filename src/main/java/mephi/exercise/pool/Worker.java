package mephi.exercise.pool;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Класс-задача
 */
@Slf4j
@RequiredArgsConstructor
public class Worker implements Runnable {

    private boolean running = true;
    private final int id;
    private final InterruptedFunction<Runnable> supplierTask;
    private final InterruptedFunction<Status> supplierCheck;
    @Getter
    private Status status = Status.WAITING;

    @Override
    public void run() {
        try {
            do {
                Runnable task;
                while (running && (task = supplierTask.apply(id)) != null) {
                    status = Status.RUNNING;
                    task.run();
                }
            } while ((status = supplierCheck.apply(id)) != Status.STOPPED);
            log.info("Worker was stopped");
        } catch (InterruptedException e) {
            status = Status.STOPPED;
            log.error(e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Передача сигнала об остановке
     */
    public void stop() {
        running = false;
    }
}
