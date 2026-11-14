package br.dev.guisleri.agendaarena.match.controller;

import br.dev.guisleri.agendaarena.common.dto.PageRequests;
import br.dev.guisleri.agendaarena.common.dto.PageResponseDTO;
import br.dev.guisleri.agendaarena.match.dto.AdminMatchResponseDTO;
import br.dev.guisleri.agendaarena.match.dto.AdminMatchSummaryResponseDTO;
import br.dev.guisleri.agendaarena.match.model.MatchStatus;
import br.dev.guisleri.agendaarena.match.service.AdminMatchListService;
import br.dev.guisleri.agendaarena.match.service.MatchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Partidas do estabelecimento para o administrador (gestão, somente leitura).
 */
@RestController
@RequestMapping("/establishments/{establishmentId}/matches")
public class AdminMatchController {

    private final AdminMatchListService adminMatchListService;
    private final MatchService matchService;

    public AdminMatchController(
            AdminMatchListService adminMatchListService,
            MatchService matchService
    ) {
        this.adminMatchListService = adminMatchListService;
        this.matchService = matchService;
    }

    @GetMapping
    public ResponseEntity<PageResponseDTO<AdminMatchSummaryResponseDTO>> listMatches(
            @PathVariable Long establishmentId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Long sportFieldId,
            @RequestParam(required = false) List<MatchStatus> status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_SIZE) int size
    ) {
        return ResponseEntity.ok(
                adminMatchListService.list(
                        establishmentId,
                        from,
                        to,
                        sportFieldId,
                        status,
                        page,
                        size
                )
        );
    }

    @GetMapping("/{matchId}")
    public ResponseEntity<AdminMatchResponseDTO> findMatch(
            @PathVariable Long establishmentId,
            @PathVariable Long matchId
    ) {
        return ResponseEntity.ok(AdminMatchResponseDTO.fromDetails(
                matchService.findEstablishmentMatch(establishmentId, matchId)
        ));
    }
}
