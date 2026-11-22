package com.sgmart.inventory;

import com.sgmart.catalog.Product;
import com.sgmart.catalog.ProductRepository;
import com.sgmart.common.ApiException;
import com.sgmart.inventory.StockDtos.Mode;
import com.sgmart.inventory.StockDtos.StockChangeResult;
import com.sgmart.inventory.StockDtos.StockUpdateRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.TreeMap;

/**
 * Every stock change goes through here, so stock_movements is always complete.
 * Product rows are locked (SELECT ... FOR UPDATE) while stock changes.
 */
@Service
public class StockService {

    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final ApplicationEventPublisher publisher;

    public StockService(ProductRepository products, StockMovementRepository movements,
                        ApplicationEventPublisher publisher) {
        this.products = products;
        this.movements = movements;
        this.publisher = publisher;
    }

    /** Admin screens refresh stock badges when this arrives (sent after the transaction commits). */
    private void changed(Product p) {
        publisher.publishEvent(new StockChangedEvent(p.getId(), p.getStockQty(), StockStatus.of(p.isActive(), p.getStockQty())));
    }

    /** Admin "Update stock" window: add received stock, or set an exact count with a reason. */
    @Transactional
    public StockChangeResult adjustByAdmin(Long productId, StockUpdateRequest request, Long adminId) {
        Product p = lockLive(productId);
        int before = p.getStockQty();
        int after;
        StockReason reason;
        if (request.mode() == Mode.ADD) {
            if (request.qty() <= 0) {
                throw ApiException.badRequest("QTY_REQUIRED", "Enter how many units you received.", "qty");
            }
            after = before + request.qty();
            reason = StockReason.RECEIVED;
        } else {
            after = request.qty();
            reason = request.reason() == null ? StockReason.SHELF_COUNT : request.reason();
            if (!reason.isExactCountReason()) {
                throw ApiException.badRequest("INVALID_REASON",
                        "Choose Shelf count, Damaged or expired, or Correction.", "reason");
            }
        }

        int change = after - before;
        if (change != 0) {
            p.setStockQty(after);
            movements.save(new StockMovement(p.getId(), change, after, reason, null, reason.label(), adminId));
            changed(p);
        }
        return new StockChangeResult(p.getId(), p.getName(), before, after, change != 0,
                StockStatus.of(p.isActive(), after));
    }

    /** Opening stock of a newly added product, so Today's stock shows it as "added". */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordNewProduct(Product product, Long adminId) {
        if (product.getStockQty() > 0) {
            movements.save(new StockMovement(product.getId(), product.getStockQty(), product.getStockQty(),
                    StockReason.NEW_PRODUCT, null, StockReason.NEW_PRODUCT.label(), adminId));
        }
    }

    /**
     * Takes stock for an order (placed, or items added in an edit). All or nothing:
     * if any item is short, the caller's transaction rolls back and nothing is taken.
     * Rows are locked in product-id order so two orders can't deadlock each other.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void takeForOrder(Map<Long, Integer> qtyByProductId, Long orderId, StockReason reason) {
        for (Map.Entry<Long, Integer> e : new TreeMap<>(qtyByProductId).entrySet()) {
            int qty = e.getValue();
            if (qty <= 0) {
                continue;
            }
            Product p = products.findByIdForUpdate(e.getKey())
                    .orElseThrow(() -> ApiException.badRequest("PRODUCT_UNAVAILABLE",
                            "A product in your cart is no longer available.", null));
            if (!p.isActive() || p.isRemoved()) {
                throw ApiException.conflict("PRODUCT_UNAVAILABLE", p.getName() + " is no longer available.");
            }
            if (p.getStockQty() < qty) {
                String message = p.getStockQty() == 0
                        ? p.getName() + " just went out of stock."
                        : "Only " + p.getStockQty() + " " + p.getName() + " left.";
                throw ApiException.conflict("STOCK_NOT_ENOUGH", message);
            }
            int after = p.getStockQty() - qty;
            p.setStockQty(after);
            movements.save(new StockMovement(p.getId(), -qty, after, reason, orderId, reason.label(), null));
            changed(p);
        }
    }

    /** Puts stock back (item not available, cancelled, rejected, or removed in an edit). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void returnForOrder(Map<Long, Integer> qtyByProductId, Long orderId, StockReason reason, Long adminId) {
        for (Map.Entry<Long, Integer> e : new TreeMap<>(qtyByProductId).entrySet()) {
            int qty = e.getValue();
            if (qty <= 0) {
                continue;
            }
            Product p = products.findByIdForUpdate(e.getKey())
                    .orElseThrow(() -> new IllegalStateException("Product " + e.getKey() + " is missing"));
            int after = p.getStockQty() + qty;
            p.setStockQty(after);
            movements.save(new StockMovement(p.getId(), qty, after, reason, orderId, reason.label(), adminId));
            changed(p);
        }
    }

    private Product lockLive(Long productId) {
        Product p = products.findByIdForUpdate(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found."));
        if (p.isRemoved()) {
            throw ApiException.notFound("This product was removed.");
        }
        return p;
    }
}
