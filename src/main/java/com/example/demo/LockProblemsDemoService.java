package com.example.demo;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;

/**
 * Класс сервиса для демонстрации Deadlock, Livelock и Starvation. Этапы:
 * моделирование deadlock с диагностикой (состояния потоков, ThreadMXBean,
 * окно для дампа jstack), предотвращение deadlock через единый порядок
 * захвата, livelock и выход из него, starvation и справедливая блокировка.
 */
@Service
public class LockProblemsDemoService {

    private static final long HOLD_MS = 200;        // пауза между захватом первого и второго ресурса
    private static final long DEADLOCK_WAIT_MS = 2000;
    private static final long DUMP_PAUSE_MS = 10_000; // окно для снятия дампа потоков (jstack)
    private static final long LIVELOCK_RUN_MS = 1000;
    private static final int POLITENESS_LIMIT = 3;
    private static final long STARVE_RUN_MS = 2000;

    /** Демонстрация deadlock, livelock и starvation, вызывается при старте приложения. */
    public void runLockProblemsDemo() {
        try {
            runDeadlockDemo();
            runOrderedAllocationFix();
            runLivelockDemo();
            runLivelockFix();
            runStarvationDemo();
            runStarvationFix();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Демонстрация прервана");
        }
    }

    /**
     * Этап 1. Два потока захватывают resourceA и resourceB в противоположном
     * порядке — программа взаимно блокируется. JVM (ThreadMXBean) подтверждает
     * deadlock; в этой точке снимается дамп потоков jstack.
     */
    private void runDeadlockDemo() throws InterruptedException {
        System.out.println("=== Deadlock: противоположный порядок захвата ресурсов ===");
        Resource resourceA = new Resource("resourceA");
        Resource resourceB = new Resource("resourceB");
        DeadlockWorker ab = new DeadlockWorker("Deadlock-1", resourceA, resourceB, HOLD_MS);
        DeadlockWorker ba = new DeadlockWorker("Deadlock-2", resourceB, resourceA, HOLD_MS);
        // заблокированные навсегда потоки-демоны не мешают дальше работать приложению
        Thread abThread = new Thread(ab, "Deadlock-1");
        Thread baThread = new Thread(ba, "Deadlock-2");
        abThread.setDaemon(true);
        baThread.setDaemon(true);
        abThread.start();
        baThread.start();

        abThread.join(DEADLOCK_WAIT_MS);
        baThread.join(DEADLOCK_WAIT_MS); // оба тайм-аута истекли: потоки не завершились
        System.out.println("Состояния через " + DEADLOCK_WAIT_MS + " мс: Deadlock-1 = "
                + abThread.getState() + ", Deadlock-2 = " + baThread.getState());

        ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
        long[] deadlockedIds = threadMxBean.findDeadlockedThreads();
        if (deadlockedIds == null) {
            System.out.println("ThreadMXBean: взаимная блокировка не обнаружена");
        } else {
            for (long id : deadlockedIds) {
                ThreadInfo info = threadMxBean.getThreadInfo(id);
                System.out.println("ThreadMXBean: deadlock — поток " + info.getThreadName()
                        + " в состоянии " + info.getThreadState() + ", ждёт монитор " + info.getLockName());
            }
        }
        System.out.println("Основной поток: пауза " + DUMP_PAUSE_MS / 1000 + " с — окно для снятия дампа потоков (jstack)");
        Thread.sleep(DUMP_PAUSE_MS);
        System.out.println("Оба потока так и не завершились — программа в этой точке зависла бы навсегда");
    }

    /** Этап 2. Предотвращение deadlock: оба потока захватывают ресурсы в одном порядке. */
    private void runOrderedAllocationFix() throws InterruptedException {
        System.out.println("=== Предотвращение deadlock: единый порядок захвата ===");
        Resource resourceA = new Resource("resourceA");
        Resource resourceB = new Resource("resourceB");
        DeadlockWorker first = new DeadlockWorker("Fix-1", resourceA, resourceB, HOLD_MS);
        DeadlockWorker second = new DeadlockWorker("Fix-2", resourceA, resourceB, HOLD_MS);
        // порядок одинаковый — циклическое ожидание невозможно, ждать можно без тайм-аута
        Thread firstThread = new Thread(first, "Fix-1");
        Thread secondThread = new Thread(second, "Fix-2");
        firstThread.start();
        secondThread.start();
        firstThread.join();
        secondThread.join();
        System.out.println("Оба потока завершились: Fix-1 = " + first.isCompleted()
                + ", Fix-2 = " + second.isCompleted() + " — взаимная блокировка невозможна");
    }

    /** Этап 3. Livelock: оба потока постоянно уступают друг другу, задач никто не выполнил. */
    private void runLivelockDemo() throws InterruptedException {
        System.out.println("=== Livelock: бесконечные уступки друг другу ===");
        SharedResource resource = new SharedResource("Livelock-1");
        resource.setHungry("Livelock-1", true);
        resource.setHungry("Livelock-2", true);
        LivelockWorker first = new LivelockWorker("Livelock-1", "Livelock-2", resource, Integer.MAX_VALUE);
        LivelockWorker second = new LivelockWorker("Livelock-2", "Livelock-1", resource, Integer.MAX_VALUE);
        Thread firstThread = new Thread(first, "Livelock-1");
        Thread secondThread = new Thread(second, "Livelock-2");
        firstThread.start();
        secondThread.start();
        Thread.sleep(LIVELOCK_RUN_MS);
        first.stop();
        second.stop();
        firstThread.join();
        secondThread.join();
        System.out.println("За " + LIVELOCK_RUN_MS + " мс: уступок — " + (first.getTotalTransfers() + second.getTotalTransfers())
                + ", задач выполнено — " + (first.getMeals() + second.getMeals())
                + ": потоки активны, но без прогресса");
    }

    /** Этап 4. Выход из livelock: уступчивость ограничена лимитом, задачи выполняются. */
    private void runLivelockFix() throws InterruptedException {
        System.out.println("=== Выход из livelock: ограничение числа уступок ===");
        SharedResource resource = new SharedResource("Fix-1");
        resource.setHungry("Fix-1", true);
        resource.setHungry("Fix-2", true);
        LivelockWorker first = new LivelockWorker("Fix-1", "Fix-2", resource, POLITENESS_LIMIT);
        LivelockWorker second = new LivelockWorker("Fix-2", "Fix-1", resource, POLITENESS_LIMIT);
        Thread firstThread = new Thread(first, "Fix-1");
        Thread secondThread = new Thread(second, "Fix-2");
        firstThread.start();
        secondThread.start();
        while (first.getMeals() < 1 || second.getMeals() < 1) {
            Thread.sleep(10); // ждём, пока обе задачи выполнятся
        }
        first.stop();
        second.stop();
        firstThread.join();
        secondThread.join();
        System.out.println("Уступок — " + (first.getTotalTransfers() + second.getTotalTransfers())
                + ", каждая задача выполнена — прогресс вернулся");
    }

    /** Этап 5. Starvation: несправедливая блокировка и плотный цикл конкурента. */
    private void runStarvationDemo() throws InterruptedException {
        System.out.println("=== Starvation: несправедливая блокировка ===");
        ReentrantLock unfairLock = new ReentrantLock(false);
        runStarvationScenario(unfairLock);
    }

    /** Этап 6. Предотвращение starvation: справедливая (fair) блокировка. */
    private void runStarvationFix() throws InterruptedException {
        System.out.println("=== Предотвращение starvation: справедливая блокировка ===");
        ReentrantLock fairLock = new ReentrantLock(true);
        runStarvationScenario(fairLock);
    }

    private void runStarvationScenario(ReentrantLock lock) throws InterruptedException {
        HoggingWorker hogger = new HoggingWorker(lock);
        StarvedWorker starved = new StarvedWorker(lock);
        Thread hoggerThread = new Thread(hogger, "Жадный");
        Thread starvedThread = new Thread(starved, "Голодный");
        hoggerThread.start();
        starvedThread.start();
        Thread.sleep(STARVE_RUN_MS);
        hogger.stop();
        starved.stop();
        hoggerThread.join();
        starvedThread.join();
        System.out.println("За " + STARVE_RUN_MS + " мс: Жадный получил ресурс " + hogger.getGrants()
                + " раз, Голодный — " + starved.getGrants()
                + " раз (неудачных попыток: " + starved.getFailures() + ")");
    }
}