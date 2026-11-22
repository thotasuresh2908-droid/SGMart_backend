package com.sgmart.catalog;

import com.sgmart.catalog.ProductDtos.ShopProduct;
import com.sgmart.common.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Shop side: only products that are live and in stock. */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    /** /api/v1/products?category=2&q=milk&page=0&size=40 */
    @GetMapping
    public PageResult<ShopProduct> list(@RequestParam(name = "category", required = false) Long categoryId,
                                        @RequestParam(name = "q", required = false) String q,
                                        @RequestParam(name = "page", defaultValue = "0") int page,
                                        @RequestParam(name = "size", defaultValue = "40") int size) {
        return service.shop(categoryId, q, page, size);
    }

    @GetMapping("/{id}")
    public ShopProduct get(@PathVariable Long id) {
        return service.shopProduct(id);
    }
}
