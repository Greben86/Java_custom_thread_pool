package mephi.exercise.pool;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

/**
 * Интерфейс пула потоков
 */
public interface CustomExecutor extends Executor {

    /**
     * Добавить задачу для выполнения
     *
     * @param command the runnable task
     */
    void execute(Runnable command);

    /**
     * Добавить задачу для выполнения с возвращаемым значением
     *
     * @param callable задача с возвращаемым значением
     * @return результат выполнения задача {@link Future}
     * @param <T> тип возвращаемого значения
     */
    <T> Future<T> submit(Callable<T> callable);

    /**
     * Завершение работы - новые задачи не могут быть приняты, потоки выполняют свои задачи и останавливаются
     */
    void shutdown();

    /**
     * Немедленное завершение работы - потоки завершают свои текущие задачи и закрываются
     */
    void shutdownNow();
}
