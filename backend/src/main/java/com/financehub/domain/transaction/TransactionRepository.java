package com.financehub.domain.transaction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByUserIdOrderByTxnDateDescIdDesc(Long userId);

    List<Transaction> findByUserIdAndTxnDateBetweenOrderByTxnDateDescIdDesc(
            Long userId, LocalDate from, LocalDate to);

    Optional<Transaction> findByIdAndUserId(Long id, Long userId);
}
