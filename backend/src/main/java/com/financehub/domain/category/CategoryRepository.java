package com.financehub.domain.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("""
        SELECT c FROM Category c
        WHERE c.system = true OR c.userId = :userId
        ORDER BY c.system DESC, c.kind ASC, c.name ASC
    """)
    List<Category> findVisibleTo(@Param("userId") Long userId);

    @Query("""
        SELECT c FROM Category c
        WHERE c.id = :id AND (c.system = true OR c.userId = :userId)
    """)
    Optional<Category> findVisibleToUser(@Param("id") Long id, @Param("userId") Long userId);
}
