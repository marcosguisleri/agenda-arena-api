package br.dev.guisleri.agendaarena.match.controller;

import br.dev.guisleri.agendaarena.match.dto.PublicMatchResponseDTO;
import br.dev.guisleri.agendaarena.match.service.MatchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Página pública da partida, acessada pelo link compartilhado (sem conta).
 */
@RestController
@RequestMapping("/matches")
public class PublicMatchController {

    private final MatchService matchService;

    public PublicMatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @GetMapping("/{publicToken}")
    public ResponseEntity<PublicMatchResponseDTO> findMatch(@PathVariable String publicToken) {
        return ResponseEntity.ok(
                PublicMatchResponseDTO.fromEntity(matchService.findPublicMatch(publicToken))
        );
    }
}
