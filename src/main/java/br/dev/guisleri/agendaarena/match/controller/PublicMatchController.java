package br.dev.guisleri.agendaarena.match.controller;

import br.dev.guisleri.agendaarena.match.dto.JoinMatchRequestDTO;
import br.dev.guisleri.agendaarena.match.dto.MatchSummaryResponseDTO;
import br.dev.guisleri.agendaarena.match.dto.PublicMatchResponseDTO;
import br.dev.guisleri.agendaarena.match.service.MatchService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Página pública da partida, acessada pelo link compartilhado (sem conta).
 */
@RestController
@RequestMapping("/matches/{publicToken}")
public class PublicMatchController {

    private final MatchService matchService;

    public PublicMatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @GetMapping
    public ResponseEntity<PublicMatchResponseDTO> findMatch(@PathVariable String publicToken) {
        return ResponseEntity.ok(
                PublicMatchResponseDTO.fromDetails(matchService.findPublicMatch(publicToken))
        );
    }

    @GetMapping("/summary")
    public ResponseEntity<MatchSummaryResponseDTO> findSummary(@PathVariable String publicToken) {
        return ResponseEntity.ok(
                MatchSummaryResponseDTO.fromDetails(matchService.findPublicMatch(publicToken))
        );
    }

    /**
     * 201 com a página atualizada (a cota de todos muda a cada inscrição).
     */
    @PostMapping("/participants")
    public ResponseEntity<PublicMatchResponseDTO> joinMatch(
            @PathVariable String publicToken,
            @Valid @RequestBody JoinMatchRequestDTO request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(PublicMatchResponseDTO.fromDetails(
                        matchService.joinMatch(publicToken, request.name(), request.position())
                ));
    }
}
