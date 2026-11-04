package com.example.demo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Поток-наблюдатель: опрашивает состояния отслеживаемых потоков и печатает в консоль
 * каждое изменение состояния. Завершается, когда все наблюдаемые потоки TERMINATED.
 */
public class ThreadStateObserver implements Runnable {

    private static final long PERIOD_MS = 100; // период опроса состояний

    private final List<Thread> watched;

    public ThreadStateObserver(List<Thread> watched) {
        this.watched = watched;
    }

    @Override
    public void run() {
        Map<Thread, Thread.State> previous = new HashMap<>();
        while (true) {
            boolean allTerminated = true;
            for (Thread thread : watched) {
                Thread.State state = thread.getState();
                if (state != previous.get(thread)) {
                    System.out.println("[наблюдатель] " + thread.getName() + ": " + state);
                    previous.put(thread, state);
                }
                if (state != Thread.State.TERMINATED) {
                    allTerminated = false;
                }
            }
            if (allTerminated) {
                return;
            }
            try {
                Thread.sleep(PERIOD_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}