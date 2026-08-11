package com.poupito.api.card;

import com.poupito.api.card.dto.CardRequest;
import com.poupito.api.card.dto.CardResponse;
import com.poupito.api.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/cards")
public class CardController {

	private final CardService cardService;

	public CardController(CardService cardService) {
		this.cardService = cardService;
	}

	@GetMapping
	public List<CardResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(defaultValue = "false") boolean archived) {
		return cardService.list(user.id(), archived);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CardResponse create(@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody CardRequest request) {
		return cardService.create(user.id(), request);
	}

	@PutMapping("/{id}")
	public CardResponse update(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID id, @Valid @RequestBody CardRequest request) {
		return cardService.update(user.id(), id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
		cardService.delete(user.id(), id);
	}

	@PatchMapping("/{id}/archive")
	public CardResponse archive(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
		return cardService.archive(user.id(), id);
	}

	@PatchMapping("/{id}/unarchive")
	public CardResponse unarchive(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
		return cardService.unarchive(user.id(), id);
	}

}
