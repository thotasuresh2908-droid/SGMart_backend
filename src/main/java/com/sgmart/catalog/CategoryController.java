package com.sgmart.catalog;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Category chips on the shop home page. */
@RestController
public class CategoryController {

    public record CategoryView(Long id, String name, String icon) {
    }

    private final CategoryRepository repository;

    public CategoryController(CategoryRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/v1/categories")
    @Transactional(readOnly = true)
    public List<CategoryView> list() {
        return repository.findAllByActiveTrueOrderBySortOrderAscNameAsc().stream()
                .map(c -> new CategoryView(c.getId(), c.getName(), c.getIcon()))
                .toList();
    }
}
