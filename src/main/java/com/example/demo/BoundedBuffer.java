package com.example.demo;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Ограниченный буфер фиксированного размера на ReentrantLock и Condition.
 * put() добавляет элемент, take() извлекает. Если буфер полон, производитель
 * ждёт на условии notFull; если пуст, потребитель ждёт на notEmpty. У каждого
 * условия своя очередь ожидания — производитель будит потребителя и наоборот,
 * лишних пробуждений нет.
 */
public class BoundedBuffer<T> implements Buffer<T> {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();  // ждут производители, пока буфер полон
    private final Condition notEmpty = lock.newCondition(); // ждут потребители, пока буфер пуст

    private final Object[] items;
    private int head; // позиция извлечения
    private int tail; // позиция вставки
    private int count;

    /** Производители закончили подачу — потребителям пора завершаться. */
    private boolean productionFinished;

    /** Максимальное заполнение, зафиксированное за время работы. */
    private int maxSize;

    public BoundedBuffer(int capacity) {
        items = new Object[capacity];
    }

    @Override
    public void put(T item) throws InterruptedException {
        lock.lock(); // явный захват и освобождение замка, в отличие от synchronized
        try {
            while (count == items.length) {
                notFull.await(); // буфер полон — производитель ждёт появления места
            }
            items[tail] = item;
            tail = (tail + 1) % items.length;
            count++;
            if (count > maxSize) {
                maxSize = count;
            }
            notEmpty.signal(); // появился элемент — будим одного потребителя
        } finally {
            lock.unlock();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public T take() throws InterruptedException {
        lock.lock();
        try {
            while (count == 0) {
                if (productionFinished) {
                    return null; // подача закончена и буфер пуст — потребитель завершает работу
                }
                notEmpty.await(); // буфер пуст — потребитель ждёт появления элементов
            }
            T item = (T) items[head];
            items[head] = null;
            head = (head + 1) % items.length;
            count--;
            notFull.signal(); // появилось место — будим одного производителя
            return item;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void finishProduction() {
        lock.lock();
        try {
            productionFinished = true;
            notEmpty.signalAll(); // будим всех потребителей, чтобы проверили условие
        } finally {
            lock.unlock();
        }
    }

    public int getMaxSize() {
        lock.lock();
        try {
            return maxSize;
        } finally {
            lock.unlock();
        }
    }
}