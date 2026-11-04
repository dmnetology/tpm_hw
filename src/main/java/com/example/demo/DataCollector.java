package com.example.demo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Общий ресурс, с которым одновременно работают несколько потоков:
 * список собранных элементов, набор ключей обработанных элементов и их счётчик.
 * Все операции объявлены synchronized — изменение общих данных в каждый момент
 * выполняет только один поток, поэтому гонки данных исключены. Координация
 * потоков построена на wait()/notifyAll(): если данных нет, поток-обработчик
 * засыпает, а производитель, добавив данные, будит ожидающих.
 */
public class DataCollector {

    private final String name;

    /** Общий список собранных элементов. */
    private final List<Item> pendingItems = new ArrayList<>();
    /** Ключи обработанных элементов — защита от дублирования работы. */
    private final Set<String> processedKeys = new HashSet<>();
    /** Счётчик обработанных элементов. */
    private int processedCount = 0;

    /** Производители закончили подачу элементов. */
    private boolean productionFinished = false;

    public DataCollector(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /** Добавляет элемент в общий список и уведомляет ждущие потоки. */
    public synchronized void collectItem(Item item) {
        pendingItems.add(item);
        // данные в общей структуре изменились — будим всех ожидающих
        notifyAll();
    }

    /**
     * Забирает следующий элемент из общего списка. Если данных нет, поток
     * засыпает (wait()) — пока другой поток не изменит данные и не разбудит
     * его через notifyAll(). Возвращает null, когда подача элементов закончена
     * и список пуст.
     */
    public synchronized Item takeNext() throws InterruptedException {
        while (pendingItems.isEmpty()) {
            if (productionFinished) {
                return null;
            }
            wait(); // данных нет — ждём уведомления об их появлении
        }
        return pendingItems.remove(pendingItems.size() - 1);
    }

    /** Сообщает, что производители закончили работу, и будит ожидающих обработчиков. */
    public synchronized void finishProduction() {
        productionFinished = true;
        notifyAll();
    }

    /** Помечает ключ как обработанный. */
    public synchronized void markProcessed(String key) {
        processedKeys.add(key);
    }

    /** Проверяет, обрабатывался ли элемент, чтобы не дублировать работу. */
    public synchronized boolean isAlreadyProcessed(String key) {
        return processedKeys.contains(key);
    }

    /** Увеличивает счётчик обработанных элементов. */
    public synchronized void incrementProcessed() {
        processedCount++;
    }

    public synchronized int getProcessedCount() {
        return processedCount;
    }

    public synchronized int getProcessedKeyCount() {
        return processedKeys.size();
    }

    public synchronized int getPendingCount() {
        return pendingItems.size();
    }

    /**
     * Переносит элементы из одного коллектора в другой. Здесь поток держит
     * сразу два замка — как при переводе средств между счетами. Чтобы не
     * получить взаимную блокировку, замки всегда захватываются в одном и том
     * же порядке: сначала коллектор с меньшим identityHashCode. Без единого
     * порядка два встречных переноса могли бы навсегда заблокировать друг
     * друга (deadlock).
     */
    public static void transferItems(DataCollector from, DataCollector to, int amount) {
        if (from == to) {
            return;
        }
        DataCollector first = System.identityHashCode(from) < System.identityHashCode(to) ? from : to;
        DataCollector second = first == from ? to : from;
        synchronized (first) {
            synchronized (second) {
                int moved = 0;
                while (!from.pendingItems.isEmpty() && moved < amount) {
                    Item item = from.pendingItems.remove(from.pendingItems.size() - 1);
                    to.pendingItems.add(item);
                    moved++;
                }
                System.out.println(Thread.currentThread().getName() + ": перенос из "
                        + from.getName() + " в " + to.getName() + ", перенесено " + moved);
            }
        }
    }
}