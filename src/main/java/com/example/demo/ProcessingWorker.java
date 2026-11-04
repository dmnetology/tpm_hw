package com.example.demo;

/**
 * Задача потока-обработчика: забирает элементы из общего коллектора и
 * обрабатывает их, повторные ключи пропускает. Завершается, когда подача
 * элементов закончена, а общий список пуст.
 */
public class ProcessingWorker implements Runnable {

    private final DataCollector collector;

    private int processed;
    private int skippedDuplicates;

    public ProcessingWorker(DataCollector collector) {
        this.collector = collector;
    }

    @Override
    public void run() {
        try {
            while (true) {
                Item item = collector.takeNext();
                if (item == null) {
                    return; // подача закончена и список пуст — поток завершает работу
                }
                // проверка и пометка должны выполняться как одно целое, поэтому
                // оба вызова под одним замком на самом коллекторе; его
                // synchronized-методы реентерабельны — второй вход в монитор
                // тем же потоком не блокирует
                synchronized (collector) {
                    if (collector.isAlreadyProcessed(item.key())) {
                        skippedDuplicates++;
                    } else {
                        collector.markProcessed(item.key());
                        collector.incrementProcessed();
                        processed++;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // восстановить статус прерывания
        }
    }

    public int getProcessed() {
        return processed;
    }

    public int getSkippedDuplicates() {
        return skippedDuplicates;
    }
}