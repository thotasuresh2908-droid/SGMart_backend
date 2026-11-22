package com.sgmart.catalog;

import com.sgmart.catalog.ProductDtos.AdminProduct;
import com.sgmart.catalog.ProductDtos.CreateProductRequest;
import com.sgmart.catalog.ProductDtos.ShopProduct;
import com.sgmart.catalog.ProductDtos.UpdateProductRequest;
import com.sgmart.common.ApiException;
import com.sgmart.common.PageResult;
import com.sgmart.inventory.StockService;
import com.sgmart.media.ImageProcessor;
import com.sgmart.media.ImageProcessor.ProcessedImage;
import com.sgmart.media.ImageStorage;
import com.sgmart.media.InvalidImageException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class ProductService {

    public static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final StockService stock;
    private final ImageStorage images;
    private final Clock clock;

    public ProductService(ProductRepository products, CategoryRepository categories, StockService stock,
                          ImageStorage images, Clock clock) {
        this.products = products;
        this.categories = categories;
        this.stock = stock;
        this.images = images;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- shop (public)

    @Transactional(readOnly = true)
    public PageResult<ShopProduct> shop(Long categoryId, String search, int page, int size) {
        String q = (search == null || search.isBlank()) ? null
                : "%" + search.trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("category.sortOrder", "name"));
        return PageResult.of(products.findForShop(categoryId, q, pageRequest), ShopProduct::of);
    }

    @Transactional(readOnly = true)
    public ShopProduct shopProduct(Long id) {
        return products.findLive(id)
                .filter(p -> p.isSellable() && p.getCategory().isActive())
                .map(ShopProduct::of)
                .orElseThrow(() -> ApiException.notFound("This product isn't available right now."));
    }

    // ---------------------------------------------------------------- admin

    /** All products that aren't removed, problems first (out of stock, low stock), then by name. */
    @Transactional(readOnly = true)
    public List<AdminProduct> adminList() {
        return products.findAllByDeletedAtIsNull().stream()
                .map(AdminProduct::of)
                .sorted(Comparator.comparingInt((AdminProduct p) -> p.status().sortRank())
                        .thenComparing(AdminProduct::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public AdminProduct create(CreateProductRequest request, Long adminId) {
        Category category = activeCategory(request.categoryId());
        BigDecimal mrp = checkedMrp(request.price(), request.mrp());
        Product p = products.save(Product.create(category, request.name().trim(), request.unit().trim(),
                request.price(), mrp, request.stockQty(), request.icon()));
        stock.recordNewProduct(p, adminId);
        return AdminProduct.of(p);
    }

    /** Edit name, pack size, category, price, MRP, icon and Live/Hidden. New prices apply to new orders only. */
    @Transactional
    public AdminProduct update(Long id, UpdateProductRequest request) {
        Product p = live(id);
        p.setCategory(activeCategory(request.categoryId()));
        p.setName(request.name().trim());
        p.setUnit(request.unit().trim());
        p.setPrice(request.price());
        p.setMrp(checkedMrp(request.price(), request.mrp()));
        if (request.icon() != null && !request.icon().isBlank()) {
            p.setIcon(request.icon());
        }
        p.setActive(request.active());
        return AdminProduct.of(products.saveAndFlush(p));
    }

    /** "Remove" in Admin: hidden everywhere, but kept so past orders still show it. */
    @Transactional
    public void remove(Long id) {
        live(id).remove(Instant.now(clock));
    }

    @Transactional
    public AdminProduct setImage(Long id, byte[] upload) {
        Product p = live(id);
        ProcessedImage image;
        try {
            image = ImageProcessor.process(upload);
        } catch (InvalidImageException e) {
            throw ApiException.badRequest("INVALID_IMAGE", e.getMessage(), "file");
        }
        String oldKey = p.getImageKey();
        p.setImageKey(images.save(image.bytes(), image.extension()));
        AdminProduct result = AdminProduct.of(products.saveAndFlush(p));
        if (oldKey != null) {
            images.delete(oldKey);
        }
        return result;
    }

    @Transactional
    public AdminProduct removeImage(Long id) {
        Product p = live(id);
        String oldKey = p.getImageKey();
        p.setImageKey(null);
        AdminProduct result = AdminProduct.of(products.saveAndFlush(p));
        if (oldKey != null) {
            images.delete(oldKey);
        }
        return result;
    }

    // ---------------------------------------------------------------- helpers

    private Product live(Long id) {
        return products.findLive(id).orElseThrow(() -> ApiException.notFound("Product not found."));
    }

    private Category activeCategory(Long id) {
        return categories.findById(id)
                .filter(Category::isActive)
                .orElseThrow(() -> ApiException.badRequest("INVALID_CATEGORY", "Choose a category from the list.", "categoryId"));
    }

    /** Empty MRP means "same as price". MRP below the selling price is refused (as in the prototype). */
    static BigDecimal checkedMrp(BigDecimal price, BigDecimal mrp) {
        if (mrp == null) {
            return price;
        }
        if (mrp.compareTo(price) < 0) {
            throw ApiException.badRequest("MRP_BELOW_PRICE", "MRP can't be lower than the selling price.", "mrp");
        }
        return mrp;
    }
}
