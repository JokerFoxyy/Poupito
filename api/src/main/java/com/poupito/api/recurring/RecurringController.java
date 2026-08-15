package com.poupito.api.recurring;

import com.poupito.api.common.security.AuthenticatedUser;
import com.poupito.api.recurring.dto.OccurrenceResponse;
import com.poupito.api.recurring.dto.RecurringRequest;
import com.poupito.api.recurring.dto.RecurringResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/recurring")
public class RecurringController {

	private final RecurringService recurringService;
	private final RecurringMaterializationService materializationService;

	public RecurringController(RecurringService recurringService,
			RecurringMaterializationService materializationService) {
		this.recurringService = recurringService;
		this.materializationService = materializationService;
	}

	@GetMapping
	public List<RecurringResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(defaultValue = "false") boolean archived) {
		return recurringService.list(user.id(), archived);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public RecurringResponse create(@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody RecurringRequest request) {
		return recurringService.create(user.id(), request);
	}

	@PutMapping("/{id}")
	public RecurringResponse update(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID id, @Valid @RequestBody RecurringRequest request) {
		return recurringService.update(user.id(), id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
		recurringService.delete(user.id(), id);
	}

	@PatchMapping("/{id}/archive")
	public RecurringResponse archive(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
		return recurringService.archive(user.id(), id);
	}

	@PatchMapping("/{id}/unarchive")
	public RecurringResponse unarchive(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
		return recurringService.unarchive(user.id(), id);
	}

	/** Ocorrências dos fixos ativos no mês (só leitura), sem materializar. */
	@GetMapping("/occurrences")
	public List<OccurrenceResponse> occurrences(@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
		return materializationService.occurrencesFor(user.id(), monthOrCurrent(month));
	}

	/** Materializa (idempotente) os fixos ativos do mês e devolve as ocorrências. */
	@PostMapping("/materialize")
	public List<OccurrenceResponse> materialize(@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
		return materializationService.materializeForUserAndMonth(user.id(), monthOrCurrent(month));
	}

	private YearMonth monthOrCurrent(YearMonth month) {
		return month != null ? month : YearMonth.now();
	}

}
