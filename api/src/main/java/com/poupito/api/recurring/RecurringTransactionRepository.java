package com.poupito.api.recurring;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, UUID> {

	List<RecurringTransaction> findAllByUserIdOrderByDescriptionAsc(UUID userId);

	List<RecurringTransaction> findAllByUserIdAndArchivedOrderByDescriptionAsc(UUID userId, boolean archived);

	Optional<RecurringTransaction> findByIdAndUserId(UUID id, UUID userId);

	/** Usado só pelo job de materialização — arquivado nunca gera ocorrência nova (sessão #42). */
	List<RecurringTransaction> findAllByActiveTrueAndArchivedFalse();

	void deleteByUserId(UUID userId);

}
