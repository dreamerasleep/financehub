package com.financehub.api.category;

import com.financehub.domain.category.Category;
import com.financehub.domain.category.CategoryKind;

public final class CategoryDtos {

    private CategoryDtos() {
    }

    public record CategoryResponse(
            Long id,
            String name,
            CategoryKind kind,
            boolean system
    ) {
        public static CategoryResponse from(Category c) {
            return new CategoryResponse(c.getId(), c.getName(), c.getKind(), c.isSystem());
        }
    }
}
