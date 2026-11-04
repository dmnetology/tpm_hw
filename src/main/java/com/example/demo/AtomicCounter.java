package com.example.demo;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Потокобезопасный счётчик на AtomicInteger: инкремент выполняет CAS-операцию
 * процессора и атомарен без блокировок, поэтому параллельные инкременты из
 * нескольких потоков не теряются.
 */
public class AtomicCounter implements Counter {

    private final AtomicInteger value = new AtomicInteger();

    @Override
    public void increment() {
        value.incrementAndGet(); // атомарно: read -> inc -> write под защитой CAS
    }

    @Override
    public long get() {
        return value.get();
    }
}