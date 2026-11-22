package com.sgmart.inventory;

import com.sgmart.catalog.Category;
import com.sgmart.catalog.Product;
import com.sgmart.catalog.ProductRepository;
import com.sgmart.common.ApiException;
import com.sgmart.inventory.StockDtos.Mode;
import com.sgmart.inventory.StockDtos.StockChangeResult;
import com.sgmart.inventory.StockDtos.StockUpdateRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockServiceTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final StockMovementRepository movements = mock(StockMovementRepository.class);
    private final StockService service = new StockService(products, movements,
            mock(org.springframework.context.ApplicationEventPublisher.class));

    private Product product(long id, String name, int stock) {
        Product p = Product.create(new Category("Dairy", "🥛", 1), name, "500 ml",
                new BigDecimal("28.00"), new BigDecimal("28.00"), stock, "🥛");
        ReflectionTestUtils.setField(p, "id", id);
        when(products.findByIdForUpdate(id)).thenReturn(Optional.of(p));
        return p;
    }

    private StockMovement savedMovement() {
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(movements).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void addNewStockAddsAndLogsReceived() {
        Product tea = product(1, "Green Tea", 3);
        StockChangeResult result = service.adjustByAdmin(1L, new StockUpdateRequest(Mode.ADD, 10, null), 7L);

        assertThat(tea.getStockQty()).isEqualTo(13);
        assertThat(result.stockBefore()).isEqualTo(3);
        assertThat(result.stockAfter()).isEqualTo(13);
        assertThat(result.status()).isEqualTo(StockStatus.IN_SHOP);
        StockMovement m = savedMovement();
        assertThat(m.getChangeQty()).isEqualTo(10);
        assertThat(m.getStockAfter()).isEqualTo(13);
        assertThat(m.getReason()).isEqualTo(StockReason.RECEIVED);
        assertThat(m.getCreatedByAdminId()).isEqualTo(7L);
    }

    @Test
    void setExactCountToZeroHidesFromShopAndLogsReason() {
        Product tea = product(1, "Green Tea", 3);
        StockChangeResult result = service.adjustByAdmin(1L,
                new StockUpdateRequest(Mode.SET, 0, StockReason.DAMAGED), 7L);

        assertThat(tea.getStockQty()).isZero();
        assertThat(tea.isSellable()).isFalse();
        assertThat(result.status()).isEqualTo(StockStatus.OUT_OF_STOCK);
        StockMovement m = savedMovement();
        assertThat(m.getChangeQty()).isEqualTo(-3);
        assertThat(m.getReason()).isEqualTo(StockReason.DAMAGED);
    }

    @Test
    void sameCountIsNoChangeAndNothingIsLogged() {
        product(1, "Green Tea", 3);
        StockChangeResult result = service.adjustByAdmin(1L, new StockUpdateRequest(Mode.SET, 3, null), 7L);
        assertThat(result.changed()).isFalse();
        verify(movements, never()).save(any());
    }

    @Test
    void addingZeroIsRefused() {
        product(1, "Green Tea", 3);
        assertThatThrownBy(() -> service.adjustByAdmin(1L, new StockUpdateRequest(Mode.ADD, 0, null), 7L))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("QTY_REQUIRED");
    }

    @Test
    void orderReasonsCantBeUsedForExactCount() {
        product(1, "Green Tea", 3);
        assertThatThrownBy(() -> service.adjustByAdmin(1L,
                new StockUpdateRequest(Mode.SET, 1, StockReason.ORDER_PLACED), 7L))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_REASON");
    }

    @Test
    void removedProductsCantBeRestocked() {
        Product tea = product(1, "Green Tea", 3);
        tea.remove(Instant.now());
        assertThatThrownBy(() -> service.adjustByAdmin(1L, new StockUpdateRequest(Mode.ADD, 5, null), 7L))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("NOT_FOUND");
    }

    @Test
    void orderCantTakeMoreThanInStock() {
        product(1, "Green Tea", 2);
        assertThatThrownBy(() -> service.takeForOrder(Map.of(1L, 5), 99L, StockReason.ORDER_PLACED))
                .isInstanceOf(ApiException.class)
                .hasMessage("Only 2 Green Tea left.")
                .extracting("code").isEqualTo("STOCK_NOT_ENOUGH");
    }

    @Test
    void hiddenProductCantBeOrdered() {
        Product cola = product(1, "Cola", 20);
        cola.setActive(false);
        assertThatThrownBy(() -> service.takeForOrder(Map.of(1L, 1), 99L, StockReason.ORDER_PLACED))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("PRODUCT_UNAVAILABLE");
    }

    @Test
    void orderLocksProductsInIdOrderToAvoidDeadlocks() {
        Product milk = product(5, "Toned Milk", 80);
        Product bread = product(2, "Whole Wheat Bread", 30);

        service.takeForOrder(Map.of(5L, 2, 2L, 1), 99L, StockReason.ORDER_PLACED);

        InOrder order = inOrder(products);
        order.verify(products).findByIdForUpdate(2L);
        order.verify(products).findByIdForUpdate(5L);
        assertThat(milk.getStockQty()).isEqualTo(78);
        assertThat(bread.getStockQty()).isEqualTo(29);
    }

    @Test
    void returnedStockGoesBackWithTheOrderReason() {
        Product tea = product(1, "Green Tea", 1);
        service.returnForOrder(Map.of(1L, 1), 99L, StockReason.ITEM_UNAVAILABLE, 7L);
        assertThat(tea.getStockQty()).isEqualTo(2);
        StockMovement m = savedMovement();
        assertThat(m.getReason()).isEqualTo(StockReason.ITEM_UNAVAILABLE);
        assertThat(m.getOrderId()).isEqualTo(99L);
    }
}
