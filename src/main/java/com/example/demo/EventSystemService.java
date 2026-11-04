package com.example.demo;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Система регистрации и обработки событий пользователей веб-приложения.
 * События принимаются в неблокирующую ConcurrentLinkedQueue (offer/poll на CAS,
 * poll при пустоте возвращает null), их забирает потребитель — цикл с опросом:
 * очередь не блокирует, паузы между опросами делает сам потребитель. Каждое
 * событие рассылается слушателям из CopyOnWriteArrayList — подписка и отписка
 * в момент итерации безопасны. Статистику по типам слушатель ведёт
 * в ConcurrentHashMap атомарными методами.
 */
public class EventSystemService {

    private final Queue<UserEvent> events = new ConcurrentLinkedQueue<>();
    private final CopyOnWriteArrayList<UserEventListener> listeners = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, Integer> countsByType = new ConcurrentHashMap<>();
    private final AtomicBoolean producing = new AtomicBoolean(true);
    private final AtomicInteger processedCount = new AtomicInteger();

    /** Подписка слушателя на лету — CopyOnWriteArrayList создаёт новую копию массива. */
    public void subscribe(UserEventListener listener) {
        listeners.add(listener);
    }

    /** Публикация события производителем: offer не блокируется и не может быть отклонён. */
    public void publish(UserEvent event) {
        events.offer(event);
    }

    /** Цикл потребителя: обрабатывает очередь, пока производители не закончили и очередь не пуста. */
    public void handleEvents() {
        while (producing.get() || !events.isEmpty()) {
            UserEvent event = events.poll(); // null, если очередь пуста
            if (event == null) {
                // очередь неблокирующая — паузу между опросами делает потребитель
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                continue;
            }
            for (UserEventListener listener : listeners) {
                listener.onEvent(event);
            }
            processedCount.incrementAndGet();
        }
    }

    /** Производители закончили publishes; потребитель дообрабатывает остаток и завершается. */
    public void finishProduction() {
        producing.set(false);
    }

    public int getProcessedCount() {
        return processedCount.get();
    }

    public ConcurrentHashMap<String, Integer> getCountsByType() {
        return countsByType;
    }
}