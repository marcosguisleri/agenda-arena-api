package br.dev.guisleri.agendaarena.match.controller;

import br.dev.guisleri.agendaarena.common.security.AuthenticatedUsers;
import br.dev.guisleri.agendaarena.match.dto.MatchResponseDTO;
import br.dev.guisleri.agendaarena.match.service.MatchCreationResult;
import br.dev.guisleri.agendaarena.match.service.MatchService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Partida a partir de uma ocorrência da minha série recorrente: criar e consultar. A gestão é por
 * id em {@code /me/matches/{matchId}} ({@link MeMatchController}).
 */
@RestController
@RequestMapping("/me/recurring-reservations/{recurringReservationId}/occurrences/{date}/match")
public class MeRecurringMatchController {

    private final MatchService matchService;

    public MeRecurringMatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    /**
     * 201 ao criar; 200 com a mesma partida se ela já existia.
     */
    @PostMapping
    public ResponseEntity<MatchResponseDTO> createMatch(
            @PathVariable Long recurringReservationId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication
    ) {
        MatchCreationResult result = matchService.createOccurrenceMatch(
                AuthenticatedUsers.userId(authentication),
                recurringReservationId,
                date
        );

        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(MatchResponseDTO.fromDetails(result.details()));
    }

    @GetMapping
    public ResponseEntity<MatchResponseDTO> findMyMatch(
            @PathVariable Long recurringReservationId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication
    ) {
        return ResponseEntity.ok(MatchResponseDTO.fromDetails(
                matchService.findMyMatchByOccurrence(
                        AuthenticatedUsers.userId(authentication),
                        recurringReservationId,
                        date
                )
        ));
    }
}
