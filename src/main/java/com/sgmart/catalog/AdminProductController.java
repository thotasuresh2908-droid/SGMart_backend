package com.sgmart.catalog;

import com.sgmart.auth.SgMartPrincipal;
import com.sgmart.catalog.ProductDtos.AdminProduct;
import com.sgmart.catalog.ProductDtos.CreateProductRequest;
import com.sgmart.catalog.ProductDtos.UpdateProductRequest;
import com.sgmart.inventory.StockDtos.StockChangeResult;
import com.sgmart.inventory.StockDtos.StockUpdateRequest;
import com.sgmart.inventory.StockService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/** Admin > Products: add, edit, change price, photo, Update stock, Live/Hidden, Remove. */
@RestController
@RequestMapping("/api/v1/admin/products")
public class AdminProductController {

    private final ProductService products;
    private final StockService stock;

    public AdminProductController(ProductService products, StockService stock) {
        this.products = products;
        this.stock = stock;
    }

    @GetMapping
    public List<AdminProduct> list() {
        return products.adminList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminProduct create(@Valid @RequestBody CreateProductRequest request,
                               @AuthenticationPrincipal SgMartPrincipal admin) {
        return products.create(request, admin.getAppUserId());
    }

    @PutMapping("/{id}")
    public AdminProduct update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request) {
        return products.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id) {
        products.remove(id);
    }

    /** Multipart field name: "file". JPG, PNG or WebP, up to 5 MB. */
    @PostMapping(path = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdminProduct uploadImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) throws IOException {
        return products.setImage(id, file.getBytes());
    }

    @DeleteMapping("/{id}/image")
    public AdminProduct removeImage(@PathVariable Long id) {
        return products.removeImage(id);
    }

    /** Update stock window. Body: { "mode": "ADD", "qty": 20 } or { "mode": "SET", "qty": 12, "reason": "DAMAGED" } */
    @PostMapping("/{id}/stock")
    public StockChangeResult updateStock(@PathVariable Long id, @Valid @RequestBody StockUpdateRequest request,
                                         @AuthenticationPrincipal SgMartPrincipal admin) {
        return stock.adjustByAdmin(id, request, admin.getAppUserId());
    }
}
