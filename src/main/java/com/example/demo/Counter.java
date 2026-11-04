package com.example.demo;

/**
 * Общие операции счётчика — чтобы атомарный и неатомарные варианты
 * прогонялись в одном и том же нагрузочном тесте.
 */
public interface Counter {

    void increment();

    long get();
}