package com.example.stock.bootstrap;

import com.example.stock.model.StockItem;
import com.example.stock.repository.StockItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Inserts sample stock at startup - only when app.sample-data.enabled=true
 * AND the stock_item table is empty, so restarts never create duplicates.
 * Sample data lives in code, not in a Flyway migration, so it can never
 * reach a production database by accident.
 */
@Component
@ConditionalOnProperty(name = "app.sample-data.enabled", havingValue = "true")
public class SampleStockLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleStockLoader.class);

    private final StockItemRepository stockItemRepository;

    public SampleStockLoader(StockItemRepository stockItemRepository) {
        this.stockItemRepository = stockItemRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        long existing = stockItemRepository.count();
        if (existing > 0) {
            log.info("Sample stock skipped: stock_item already contains {} rows", existing);
            return;
        }
        List<StockItem> items = SampleStockData.lines().stream().map(line -> {
            StockItem item = new StockItem();
            item.setSku(line.sku());
            item.setQuantityOnHand(line.quantityOnHand());
            item.setReorderLevel(line.reorderLevel());
            return item;
        }).toList();
        stockItemRepository.saveAll(items);
        log.info("Sample stock loaded: {} items", items.size());
    }
}
