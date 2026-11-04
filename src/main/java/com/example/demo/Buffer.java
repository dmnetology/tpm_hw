package com.example.demo;

/**
 * Общие операции ограниченного буфера. Интерфейс нужен, чтобы производители
 * и потребители могли работать с любой реализацией — на ReentrantLock/Condition
 * и на synchronized для сравнения.
 */
public interface Buffer<T> {

    void put(T item) throws InterruptedException;

    T take() throws InterruptedException;

    void finishProduction();
}