package com.example.demo;

/**
 * Ресурс для демонстрации deadlock: у каждого ресурса собственный монитор
 * и имя для вывода. Потоки захватывают ресурсы через synchronized.
 */
public class Resource {

    private final String name;

    public Resource(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}