package com.financehub.api.category;

import com.financehub.application.category.CategoryService;
import com.financehub.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<CategoryDtos.CategoryResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return categoryService.listVisibleTo(user.id()).stream()
                .map(CategoryDtos.CategoryResponse::from)
                .toList();
    }
}
