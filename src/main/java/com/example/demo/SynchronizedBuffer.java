package com.example.demo;

/**
 * Тот же ограниченный буфер фиксированного размера, но на традиционном
 * synchronized с wait()/notifyAll() — для сравнения с вариантом на
 * ReentrantLock и Condition. Обе стороны ждут на одном общем условии,
 * поэтому метод будит всех — и потребителей, и производителей.
 */
public class SynchronizedBuffer<T> implements Buffer<T> {

    private final Object[] items;
    private int head; // позиция извлечения
    private int tail; // позиция вставки
    private int count;

    /** Производители закончили подачу — потребителям пора завершаться. */
    private boolean productionFinished;

    public SynchronizedBuffer(int capacity) {
        items = new Object[capacity];
    }

    @Override
    public synchronized void put(T item) throws InterruptedException {
        while (count == items.length) {
            wait(); // буфер полон — производитель ждёт освобождения места
        }
        items[tail] = item;
        tail = (tail + 1) % items.length;
        count++;
        notifyAll(); // одно общее условие — будить приходится всех
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized T take() throws InterruptedException {
        while (count == 0) {
            if (productionFinished) {
                return null;
            }
            wait(); // буфер пуст — потребитель ждёт появления элементов
        }
        T item = (T) items[head];
        items[head] = null;
        head = (head + 1) % items.length;
        count--;
        notifyAll();
        return item;
    }

    @Override
    public synchronized void finishProduction() {
        productionFinished = true;
        notifyAll();
    }
}