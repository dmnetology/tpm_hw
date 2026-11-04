package com.example.demo;

/**
 * Сырой товар из внешнего источника: идентификатор, название, цена в USD.
 * Категория появляется после этапа обогащения — сырая запись из источника
 * приходит с category = null.
 */
public record ProductData(String id, String title, double priceUsd, String category) {

    /** Обогащённая копия: тот же товар с заполненной категорией. */
    public ProductData withCategory(String category) {
        return new ProductData(id, title, priceUsd, category);
    }
}