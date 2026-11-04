package com.example.demo;

/**
 * Задача потока для моделирования взаимной блокировки: захватывает два ресурса
 * в заданном порядке. Если у потоков порядок противоположный, каждый держит
 * свой первый ресурс и ждёт чужой — это deadlock. Если порядок одинаковый,
 * оба потока завершаются (предотвращение через упорядоченную аллокацию).
 */
public class DeadlockWorker implements Runnable {

    private final String name;
    private final Resource first;
    private final Resource second;
    private final long holdMs;

    private boolean completed;

    public DeadlockWorker(String name, Resource first, Resource second, long holdMs) {
        this.name = name;
        this.first = first;
        this.second = second;
        this.holdMs = holdMs;
    }

    @Override
    public void run() {
        try {
            synchronized (first) {
                System.out.println(name + ": захватил " + first + ", ждёт " + second);
                Thread.sleep(holdMs); // первый ресурс закрепил, второй ещё нужен
                synchronized (second) {
                    System.out.println(name + ": захватил оба ресурса, работа выполнена");
                    completed = true;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // восстановить статус прерывания
        }
    }

    public boolean isCompleted() {
        return completed;
    }
}