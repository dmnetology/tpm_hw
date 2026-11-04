package com.example.demo;

/**
 * Итоговый объект сборки: товар, обогащённый категорией, и цена в рублях,
 * посчитанная из цены в USD и курса двух независимых источников.
 */
public record FinalResult(String id, String title, String category, double priceUsd, double usdRate, double priceRub) {

    @Override
    public String toString() {
        return id + " «" + title + "» (" + category + "): " + priceUsd + " USD × " + usdRate
                + " = " + Math.round(priceRub * 100) / 100.0 + " руб.";
    }
}