package com.example.demo;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Класс сервиса для демонстрации ограниченного буфера. Два этапа: работа
 * буфера на ReentrantLock и Condition с несколькими производителями
 * и потребителями и сравнение производительности с вариантом на
 * synchronized и wait()/notifyAll().
 */
@Service
public class BoundedBufferDemoService {

    private static final int CAPACITY = 10;
    private static final int PRODUCERS = 3;
    private static final int CONSUMERS = 3;
    private static final int ITEMS_PER_PRODUCER = 500;

    private static final int LOAD_CAPACITY = 50;
    private static final int LOAD_PRODUCERS = 4;
    private static final int LOAD_CONSUMERS = 4;
    private static final int LOAD_ITEMS_PER_PRODUCER = 25_000;
    private static final int RUNS = 3;

    /** Демонстрация работы буфера, вызывается при старте приложения. */
    public void runBoundedBufferDemo() {
        try {
            runBufferShowcase();
            compareWithSynchronized();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Демонстрация прервана");
        }
    }

    /**
     * Этап 1. Несколько производителей и потребителей одновременно работают
     * с ограниченным буфером. Проверяется целостность: количество и сумма
     * положенных значений совпадают с извлечёнными.
     */
    private void runBufferShowcase() throws InterruptedException {
        System.out.println("=== BoundedBuffer: ограниченный буфер на ReentrantLock и Condition ===");
        System.out.println("Ёмкость буфера: " + CAPACITY + ", производителей: " + PRODUCERS + "x"
                + ITEMS_PER_PRODUCER + ", потребителей: " + CONSUMERS);

        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(CAPACITY);
        List<Thread> producerThreads = new ArrayList<>();
        List<Thread> consumerThreads = new ArrayList<>();
        List<BufferProducer> producers = new ArrayList<>();
        List<BufferConsumer> consumers = new ArrayList<>();

        try {
            for (int i = 1; i <= CONSUMERS; i++) {
                BufferConsumer consumer = new BufferConsumer(buffer);
                consumers.add(consumer);
                Thread thread = new Thread(consumer, "Потребитель-" + i);
                consumerThreads.add(thread);
                thread.start();
            }
            for (int i = 1; i <= PRODUCERS; i++) {
                // значения производителей не пересекаются: у каждого свой диапазон
                BufferProducer producer = new BufferProducer(buffer, ITEMS_PER_PRODUCER,
                        (i - 1) * ITEMS_PER_PRODUCER);
                producers.add(producer);
                Thread thread = new Thread(producer, "Производитель-" + i);
                producerThreads.add(thread);
                thread.start();
            }
            for (Thread thread : producerThreads) {
                thread.join();
            }
            buffer.finishProduction();
            for (Thread thread : consumerThreads) {
                thread.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        int put = 0;
        long putSum = 0;
        int taken = 0;
        long takenSum = 0;
        for (BufferProducer producer : producers) {
            put += producer.getPutCount();
            putSum += producer.getPutSum();
        }
        for (BufferConsumer consumer : consumers) {
            taken += consumer.getTakenCount();
            takenSum += consumer.getTakenSum();
        }
        System.out.println("Положено: " + put + ", извлечено: " + taken + " — совпадают: " + (put == taken));
        System.out.println("Контрольная сумма: положено " + putSum + ", извлечено " + takenSum
                + " — совпадает: " + (putSum == takenSum));
        System.out.println("Максимальное заполнение за работу: " + buffer.getMaxSize() + " из " + CAPACITY);
    }

    /**
     * Этап 2. Сравнение производительности: одинаковый нагрузочный прогон
     * (несколько производителей и потребителей, буфер фиксированного размера)
     * выполняется на ReentrantLock/Condition и на synchronized/wait()/notifyAll().
     */
    private void compareWithSynchronized() throws InterruptedException {
        System.out.println("=== Производительность: ReentrantLock/Condition против synchronized ===");
        System.out.println("Ёмкость буфера: " + LOAD_CAPACITY + ", производителей: " + LOAD_PRODUCERS + "x"
                + LOAD_ITEMS_PER_PRODUCER + ", потребителей: " + LOAD_CONSUMERS);

        // прогрев JIT: по одному незасчитанному прогону для каждого варианта
        runLoad(new BoundedBuffer<>(LOAD_CAPACITY));
        runLoad(new SynchronizedBuffer<>(LOAD_CAPACITY));

        long totalLock = 0;
        long totalMonitor = 0;
        LoadResult lockResult = null;
        LoadResult monitorResult = null;
        for (int i = 1; i <= RUNS; i++) {
            lockResult = runLoad(new BoundedBuffer<>(LOAD_CAPACITY));
            totalLock += lockResult.nanos();
            monitorResult = runLoad(new SynchronizedBuffer<>(LOAD_CAPACITY));
            totalMonitor += monitorResult.nanos();
            System.out.printf("Запуск %d: ReentrantLock %.1f мс, synchronized %.1f мс%n",
                    i, lockResult.nanos() / 1e6, monitorResult.nanos() / 1e6);
        }
        System.out.printf("Среднее: ReentrantLock %.1f мс, synchronized %.1f мс%n",
                totalLock / (double) RUNS / 1e6, totalMonitor / (double) RUNS / 1e6);
        System.out.println("Проверка данных ReentrantLock: положено " + lockResult.put() + ", извлечено " + lockResult.taken()
                + ", суммы совпадают: " + (lockResult.putSum() == lockResult.takenSum()));
        System.out.println("Проверка данных synchronized: положено " + monitorResult.put()
                + ", извлечено " + monitorResult.taken()
                + ", суммы совпадают: " + (monitorResult.putSum() == monitorResult.takenSum()));
    }

    private record LoadResult(long nanos, int put, int taken, long putSum, long takenSum) {
    }

    private LoadResult runLoad(Buffer<Integer> buffer) throws InterruptedException {
        List<Thread> producerThreads = new ArrayList<>();
        List<Thread> consumerThreads = new ArrayList<>();
        List<BufferProducer> producers = new ArrayList<>();
        List<BufferConsumer> consumers = new ArrayList<>();
        for (int i = 1; i <= LOAD_CONSUMERS; i++) {
            BufferConsumer consumer = new BufferConsumer(buffer);
            consumers.add(consumer);
            Thread thread = new Thread(consumer, "Потребитель-" + i);
            consumerThreads.add(thread);
            thread.start();
        }
        for (int i = 1; i <= LOAD_PRODUCERS; i++) {
            BufferProducer producer = new BufferProducer(buffer, LOAD_ITEMS_PER_PRODUCER,
                    (i - 1) * LOAD_ITEMS_PER_PRODUCER);
            producers.add(producer);
            Thread thread = new Thread(producer, "Производитель-" + i);
            producerThreads.add(thread);
            thread.start();
        }
        long start = System.nanoTime();
        for (Thread thread : producerThreads) {
            thread.join();
        }
        buffer.finishProduction();
        for (Thread thread : consumerThreads) {
            thread.join();
        }

        int put = 0;
        long putSum = 0;
        int taken = 0;
        long takenSum = 0;
        for (BufferProducer producer : producers) {
            put += producer.getPutCount();
            putSum += producer.getPutSum();
        }
        for (BufferConsumer consumer : consumers) {
            taken += consumer.getTakenCount();
            takenSum += consumer.getTakenSum();
        }
        return new LoadResult(System.nanoTime() - start, put, taken, putSum, takenSum);
    }
}