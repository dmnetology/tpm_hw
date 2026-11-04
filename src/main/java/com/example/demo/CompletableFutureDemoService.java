package com.example.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

/**
 * Класс сервиса для демонстрации CompletableFuture. По списку идентификаторов
 * товаров асинхронно получает данные товара и курс USD из двух независимых
 * источников, обогащает товар категорией (тяжёлое преобразование —
 * thenApplyAsync в собственном пуле), объединяет цену и курс в итоговый объект
 * (thenCombine) и собирает их в список параллельно. Сбои обрабатываются через
 * handle — метод возвращает частичный результат. Вся асинхронная работа идёт
 * в собственном пуле с именованными демон-потоками, не в commonPool.
 */
@Service
public class CompletableFutureDemoService {

    private static final List<String> IDS = List.of("sku-1", "sku-2", "sku-3", "sku-4", "sku-5");
    private static final String BROKEN_ID = "sku-3"; // имитация сбоя одного из источников
    private static final Map<String, String> TITLES = Map.of(
            "sku-1", "Смартфон", "sku-2", "Кофемолка", "sku-3", "Ноутбук",
            "sku-4", "Кроссовки", "sku-5", "Чайник");
    private static final Map<String, Double> BASE_PRICES = Map.of(
            "sku-1", 299.0, "sku-2", 45.5, "sku-3", 799.0, "sku-4", 89.0, "sku-5", 32.0);
    private static final Map<String, String> CATEGORIES = Map.of(
            "Смартфон", "электроника", "Кофемолка", "кухня", "Ноутбук", "электроника",
            "Кроссовки", "спорт", "Чайник", "кухня");
    /** На 5 товаров до 10 параллельных запросов — пул с запасом, но не огромный. */
    private static final int POOL_SIZE = 8;

    private final AtomicInteger requestSeq = new AtomicInteger();
    private final AtomicInteger workerSeq = new AtomicInteger();
    private final ExecutorService executor = Executors.newFixedThreadPool(POOL_SIZE, task -> {
        Thread thread = new Thread(task, "cf-worker-" + workerSeq.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    /** Демонстрация CompletableFuture, вызывается при старте приложения. */
    public void runCompletableFutureDemo() {
        try {
            long syncElapsed = runSyncVersion();
            long asyncElapsed = runAsyncVersion();
            System.out.printf("Время: синхронно %d мс, асинхронно %d мс — ускорение примерно в %.1f раза%n",
                    syncElapsed, asyncElapsed, syncElapsed / (double) asyncElapsed);
        } finally {
            shutdownPool();
        }
    }

    /** Синхронная версия той же сборки — база для сравнения времени. */
    private long runSyncVersion() {
        System.out.println("=== Синхронная версия: последовательные запросы ===");
        long start = System.nanoTime();
        List<FinalResult> results = new ArrayList<>();
        for (String id : IDS) {
            int requestId = requestSeq.incrementAndGet();
            try {
                ProductData product = enrich(fetchProduct(id, requestId), requestId);
                double rate = fetchRate(requestId);
                results.add(combine(product, rate));
            } catch (Exception e) {
                System.out.println("[main | req-" + requestId + "] " + id
                        + " — сбой (" + e.getClass().getSimpleName() + ": " + e.getMessage()
                        + "), объект пропущен");
            }
        }
        printResults(results);
        return (System.nanoTime() - start) / 1_000_000;
    }

    /** Асинхронная версия: сборка всех товаров через CompletableFuture. */
    private long runAsyncVersion() {
        System.out.println("=== Асинхронная версия: CompletableFuture, все товары параллельно ===");
        long start = System.nanoTime();
        List<FinalResult> results = fetchAllDataAsync(IDS);
        printResults(results);
        return (System.nanoTime() - start) / 1_000_000;
    }

    /**
     * Основной метод задания: по каждому id строится цепочка
     * fetch → обогащение → объединение с курсом, все товары — параллельно.
     */
    public List<FinalResult> fetchAllDataAsync(List<String> ids) {
        List<CompletableFuture<FinalResult>> futures = ids.stream()
                .map(this::fetchProductChain)
                .toList();
        List<FinalResult> results = futures.stream()
                .map(CompletableFuture::join)  // цепочки уже обработали сбои через handle
                .filter(Objects::nonNull)
                .toList();
        System.out.println("Собрано параллельно: " + results.size() + " из " + ids.size()
                + " объектов (сбойный источник пропущен через handle)");
        return results;
    }

    /** Цепочка для одного id: товар → обогащение → комбинация с курсом. */
    private CompletableFuture<FinalResult> fetchProductChain(String id) {
        int requestId = requestSeq.incrementAndGet();
        CompletableFuture<ProductData> productFuture = CompletableFuture
                .supplyAsync(() -> fetchProduct(id, requestId), executor)
                // тяжёлое обогащение — в собственном пуле, а не в потоке источника
                .thenApplyAsync(product -> enrich(product, requestId), executor);
        CompletableFuture<Double> rateFuture = CompletableFuture
                .supplyAsync(() -> fetchRate(requestId), executor);
        CompletableFuture<FinalResult> combined = productFuture
                .thenCombine(rateFuture, this::combine)   // цена + курс → один объект
                .handle((result, ex) -> {                 // сбой этой задачи, не всей операции
                    if (ex != null) {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        System.out.println("[" + Thread.currentThread().getName() + " | req-" + requestId
                                + "] " + id + " — сбой (" + cause.getClass().getSimpleName()
                                + ": " + cause.getMessage() + "), объект пропущен");
                        return null;
                    }
                    return result;
                });
        // потребление готового результата по мере готовности каждой цепочки
        combined.thenAccept(result -> System.out.println("[" + Thread.currentThread().getName()
                + " | req-" + requestId + "] результат собран: " + result.id() + " «" + result.title() + "»"));
        return combined;
    }

    /** Имитация источника товаров: название и цена, случайное время ответа; sku-3 — сбой. */
    private ProductData fetchProduct(String id, int requestId) {
        long latency = ThreadLocalRandom.current().nextLong(300, 801);
        awaitMs(latency);
        if (BROKEN_ID.equals(id)) {
            throw new RuntimeException("источник товаров \"" + id + "\" недоступен");
        }
        ProductData product = new ProductData(id, TITLES.get(id), BASE_PRICES.get(id), null);
        System.out.println("[" + Thread.currentThread().getName() + " | req-" + requestId
                + "] товар " + id + " получен за " + latency + " мс");
        return product;
    }

    /** Имитация независимого источника курса USD с небольшим колебанием. */
    private Double fetchRate(int requestId) {
        long latency = ThreadLocalRandom.current().nextLong(200, 501);
        awaitMs(latency);
        double rate = Math.round((90.0 + ThreadLocalRandom.current().nextDouble(-1.0, 1.0)) * 100) / 100.0;
        System.out.println("[" + Thread.currentThread().getName() + " | req-" + requestId
                + "] курс USD " + rate + " получен за " + latency + " мс");
        return rate;
    }

    /** Тяжёлое преобразование: имитация анализа и обогащение товаром категории. */
    private ProductData enrich(ProductData product, int requestId) {
        long analysisMs = ThreadLocalRandom.current().nextLong(100, 301);
        awaitMs(analysisMs);
        String category = CATEGORIES.getOrDefault(product.title(), "прочее");
        System.out.println("[" + Thread.currentThread().getName() + " | req-" + requestId
                + "] обогащение " + product.id() + ": категория «" + category
                + "» за " + analysisMs + " мс");
        return product.withCategory(category);
    }

    /** Имитация ожидания сети/обработки: спит нужное время, следя за прерыванием. */
    private static void awaitMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Объединение двух независимых результатов в итоговый объект. */
    private FinalResult combine(ProductData product, Double rate) {
        return new FinalResult(product.id(), product.title(), product.category(),
                product.priceUsd(), rate, Math.round(product.priceUsd() * rate * 100) / 100.0);
    }

    private void printResults(List<FinalResult> results) {
        System.out.println("Итоговые объекты:");
        results.forEach(result -> System.out.println("  " + result));
    }

    /** Корректное завершение пула: сначала спокойное ожидание, затем принудительно. */
    private void shutdownPool() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    /** Пул останавливается при остановке приложения — даже если демонстрация ещё не вызывалась. */
    @PreDestroy
    public void stopPool() {
        executor.shutdownNow();
    }
}