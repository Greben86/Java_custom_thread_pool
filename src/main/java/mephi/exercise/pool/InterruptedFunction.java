package mephi.exercise.pool;

@FunctionalInterface
public interface InterruptedFunction<T> {

    T apply(int id) throws InterruptedException;
}
