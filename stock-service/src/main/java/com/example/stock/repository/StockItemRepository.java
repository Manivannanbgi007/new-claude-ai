package com.example.stock.repository;

import com.example.stock.model.StockItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Data access for stock_item.
 *
 * The @Modifying queries are ATOMIC CONDITIONAL UPDATES: the check ("is enough
 * available?") and the change happen in ONE SQL statement. MySQL locks the row
 * while it runs, so two orders can never both take the last unit.
 * Return value = number of rows changed: 1 = success, 0 = condition not met.
 */
public interface StockItemRepository extends JpaRepository<StockItem, Long> {

    Optional<StockItem> findBySku(String sku);

    boolean existsBySku(String sku);

    List<StockItem> findBySkuIn(Collection<String> skus);

    /** Items at or below their reorder level (includes out-of-stock items). */
    @Query("SELECT s FROM StockItem s WHERE s.quantityOnHand - s.quantityReserved <= s.reorderLevel")
    Page<StockItem> findLowStock(Pageable pageable);

    /** Reserve: only succeeds if enough units are still available. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE StockItem s
               SET s.quantityReserved = s.quantityReserved + :quantity,
                   s.version = s.version + 1,
                   s.updatedAt = :now
             WHERE s.sku = :sku
               AND s.quantityOnHand - s.quantityReserved >= :quantity
            """)
    int reserve(@Param("sku") String sku, @Param("quantity") int quantity, @Param("now") LocalDateTime now);

    /** Release: reserved units become available again (order cancelled). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE StockItem s
               SET s.quantityReserved = s.quantityReserved - :quantity,
                   s.version = s.version + 1,
                   s.updatedAt = :now
             WHERE s.sku = :sku
               AND s.quantityReserved >= :quantity
            """)
    int release(@Param("sku") String sku, @Param("quantity") int quantity, @Param("now") LocalDateTime now);

    /** Confirm: reserved units leave the warehouse (order completed). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE StockItem s
               SET s.quantityOnHand = s.quantityOnHand - :quantity,
                   s.quantityReserved = s.quantityReserved - :quantity,
                   s.version = s.version + 1,
                   s.updatedAt = :now
             WHERE s.sku = :sku
               AND s.quantityReserved >= :quantity
               AND s.quantityOnHand >= :quantity
            """)
    int commitReserved(@Param("sku") String sku, @Param("quantity") int quantity, @Param("now") LocalDateTime now);

    /** Adjust on-hand stock by +/- change, never below the reserved quantity or zero. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE StockItem s
               SET s.quantityOnHand = s.quantityOnHand + :change,
                   s.version = s.version + 1,
                   s.updatedAt = :now
             WHERE s.sku = :sku
               AND s.quantityOnHand + :change >= s.quantityReserved
               AND s.quantityOnHand + :change >= 0
            """)
    int adjustOnHand(@Param("sku") String sku, @Param("change") int change, @Param("now") LocalDateTime now);
}
