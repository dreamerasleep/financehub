package com.financehub.application.category;

import com.financehub.domain.category.Category;
import com.financehub.domain.category.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<Category> listVisibleTo(Long userId) {
        return categoryRepository.findVisibleTo(userId);
    }
}
