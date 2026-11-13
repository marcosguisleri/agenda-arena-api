package br.dev.guisleri.agendaarena.match.controller;

import br.dev.guisleri.agendaarena.common.security.AuthenticatedUsers;
import br.dev.guisleri.agendaarena.match.dto.RecurringOccurrenceResponseDTO;
import br.dev.guisleri.agendaarena.match.service.RecurringOccurrenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Próximas ocorrências de uma série do organizador logado (série de outro: 404).
 */
@RestController
@RequestMapping("/me/recurring-reservations/{recurringReservationId}/occurrences")
public class MeRecurringOccurrenceController {

    private final RecurringOccurrenceService recurringOccurrenceService;

    public MeRecurringOccurrenceController(
            RecurringOccurrenceService recurringOccurrenceService
    ) {
        this.recurringOccurrenceService = recurringOccurrenceService;
    }

    @GetMapping
    public ResponseEntity<List<RecurringOccurrenceResponseDTO>> findOccurrences(
            @PathVariable Long recurringReservationId,
            @RequestParam(defaultValue = "" + RecurringOccurrenceService.DEFAULT_WEEKS) int weeks,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                recurringOccurrenceService.findUpcomingOccurrences(
                        AuthenticatedUsers.userId(authentication),
                        recurringReservationId,
                        weeks
                )
        );
    }

}
