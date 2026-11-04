package com.example.demo;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Singleton-кэш на AtomicReference: хранит одно значение; при обращении
 * возвращает уже созданное, а если его нет — создаёт. Создание защищено
 * CAS-операцией compareAndSet(null, значение): из нескольких потоков её
 * выигрывает ровно один, дублей значения не бывает.
 */
public class SingletonCache {

    public static final String DEFAULT_VALUE = "значение-из-кэша";

    private final AtomicReference<String> value = new AtomicReference<>();

    public String getOrCreate() {
        String current = value.get();
        if (current != null) {
            return current; // кэш уже создан — обычное чтение, без CAS
        }
        if (value.compareAndSet(null, DEFAULT_VALUE)) {
            // CAS выиграл только один поток — сюда попадает единственный создатель
            System.out.println("[кэш] значение \"" + DEFAULT_VALUE + "\" создал поток "
                    + Thread.currentThread().getName());
            return DEFAULT_VALUE;
        }
        return value.get(); // значение успел создать другой поток
    }
}