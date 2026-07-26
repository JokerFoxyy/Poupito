package com.poupito.api.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

	List<Account> findAllByUserIdOrderByNameAsc(UUID userId);

	Optional<Account> findByIdAndUserId(UUID id, UUID userId);

	/** Nome de conta é único por usuário, sem diferenciar maiúsculas (sessão #34). */
	boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);

	void deleteByUserId(UUID userId);

}
