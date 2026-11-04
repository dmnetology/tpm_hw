package com.example.demo;

/**
 * Задача потока-производителя: кладёт в общий коллектор заданное число
 * элементов. Часть ключей намеренно повторяется — чтобы проверить работу
 * isAlreadyProcessed().
 */
public class ProducerWorker implements Runnable {

    private final DataCollector collector;
    private final int totalItems;
    private final int distinctKeys;

    /** Сколько элементов уже положил этот поток. */
    private int collected;

    public ProducerWorker(DataCollector collector, int totalItems, int distinctKeys) {
        this.collector = collector;
        this.totalItems = totalItems;
        this.distinctKeys = distinctKeys;
    }

    @Override
    public void run() {
        for (int i = 0; i < totalItems; i++) {
            // номер ключа берётся по модулю distinctKeys, поэтому часть элементов — дубли
            collector.collectItem(new Item("data-" + (i % distinctKeys)));
            collected++;
        }
    }

    public int getCollected() {
        return collected;
    }
}