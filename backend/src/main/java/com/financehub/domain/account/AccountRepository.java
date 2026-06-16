package com.financehub.domain.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByUserIdOrderByIdAsc(Long userId);

    Optional<Account> findByIdAndUserId(Long id, Long userId);
}
