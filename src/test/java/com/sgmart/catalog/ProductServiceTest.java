package com.sgmart.catalog;

import com.sgmart.catalog.ProductDtos.AdminProduct;
import com.sgmart.catalog.ProductDtos.CreateProductRequest;
import com.sgmart.common.ApiException;
import com.sgmart.inventory.StockService;
import com.sgmart.inventory.StockStatus;
import com.sgmart.media.ImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final StockService stock = mock(StockService.class);
    private final ImageStorage images = mock(ImageStorage.class);
    private final ProductService service = new ProductService(products, categories, stock, images, Clock.systemUTC());
    private final Category dairy = new Category("Dairy, bread & eggs", "🥛", 20);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dairy, "id", 2L);
        when(categories.findById(2L)).thenReturn(Optional.of(dairy));
        when(products.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(products.saveAndFlush(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private CreateProductRequest curd(String price, String mrp, int stock) {
        return new CreateProductRequest(" Curd ", 2L, "400 g", new BigDecimal(price),
                mrp == null ? null : new BigDecimal(mrp), stock, "🥛");
    }

    @Test
    void newProductIsLiveWithDiscountAndOpeningStockLogged() {
        AdminProduct p = service.create(curd("35.00", "40.00", 20), 7L);
        assertThat(p.name()).isEqualTo("Curd");
        assertThat(p.discountPercent()).isEqualTo(13);
        assertThat(p.status()).isEqualTo(StockStatus.IN_SHOP);
        verify(stock).recordNewProduct(any(Product.class), any());
    }

    @Test
    void emptyMrpMeansSameAsPrice() {
        AdminProduct p = service.create(curd("35.00", null, 20), 7L);
        assertThat(p.mrp()).isEqualByComparingTo("35.00");
        assertThat(p.discountPercent()).isNull();
    }

    @Test
    void mrpBelowPriceIsRefused() {
        assertThatThrownBy(() -> service.create(curd("50.00", "40.00", 5), 7L))
                .isInstanceOf(ApiException.class)
                .hasMessage("MRP can't be lower than the selling price.")
                .extracting("field").isEqualTo("mrp");
    }

    @Test
    void unknownCategoryIsRefused() {
        when(categories.findById(99L)).thenReturn(Optional.empty());
        CreateProductRequest bad = new CreateProductRequest("Curd", 99L, "400 g",
                new BigDecimal("35.00"), null, 5, null);
        assertThatThrownBy(() -> service.create(bad, 7L))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_CATEGORY");
    }

    @Test
    void discountRoundsLikeThePrototype() {
        assertThat(ProductDtos.discountPercent(new BigDecimal("48"), new BigDecimal("60"))).isEqualTo(20);
        assertThat(ProductDtos.discountPercent(new BigDecimal("140"), new BigDecimal("165"))).isEqualTo(15);
        assertThat(ProductDtos.discountPercent(new BigDecimal("28"), new BigDecimal("28"))).isNull();
    }

    @Test
    void notAPhotoIsRefusedWithAClearMessage() {
        Product p = Product.create(dairy, "Curd", "400 g", new BigDecimal("35"), new BigDecimal("40"), 5, null);
        when(products.findLive(1L)).thenReturn(Optional.of(p));
        assertThatThrownBy(() -> service.setImage(1L, "not a photo".getBytes()))
                .isInstanceOf(ApiException.class)
                .hasMessage("This file isn't a JPG, PNG or WebP photo.")
                .extracting("code").isEqualTo("INVALID_IMAGE");
        verify(images, never()).save(any(), anyString());
    }
}
