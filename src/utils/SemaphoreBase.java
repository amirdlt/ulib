package utils;

import javax.swing.*;
import java.util.Map;
import java.util.concurrent.Semaphore;

@FunctionalInterface
public interface SemaphoreBase<K> {
    Map<K, Semaphore> getSemaphoreMap();
    default void addSemaphore(K key, Semaphore semaphore) {
        getSemaphoreMap().put(key, semaphore);
    }
    default void addSemaphore(K key, int permits) {
        addSemaphore(key, new Semaphore(permits));
    }
    default void addSemaphore(K key) {
        addSemaphore(key, 0);
    }
    default void ignoreSemaphore(K key) {
        getSemaphore(key).release(Integer.MAX_VALUE - getSemaphore(key).availablePermits());
    }
    default Semaphore getSemaphore(K key) {
        return getSemaphoreMap().get(key);
    }
    default void release(K key, int permits) {
        getSemaphore(key).release(permits);
    }
    default void acquire(K key, int permits) {
        try {
            getSemaphore(key).acquire(permits);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    default void release(K key) {
        release(key, 1);
    }
    default void acquire(K key) {
        acquire(key, 1);
    }
    default int getPermits(K key) {
        return getSemaphore(key).availablePermits();
    }
    default Timer getReleaseTimer(K key, int permit, int delayMillis) {
        return new Timer(delayMillis, e -> release(key, permit));
    }
    default Timer getReleaseTimer(K key, int delayMillis) {
        return getReleaseTimer(key, 1, delayMillis);
    }
    default Timer getAcquireTimer(K key, int permit, int delayMillis) {
        return new Timer(delayMillis, e -> acquire(key, permit));
    }
    default Timer getAcquireTimer(K key, int delayMillis) {
        return getAcquireTimer(key, 1, delayMillis);
    }
}
