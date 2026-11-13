package br.dev.guisleri.agendaarena.match.controller;

import br.dev.guisleri.agendaarena.common.security.AuthenticatedUsers;
import br.dev.guisleri.agendaarena.match.dto.ChangeParticipantPaymentRequestDTO;
import br.dev.guisleri.agendaarena.match.dto.ChangeParticipantPaysShareRequestDTO;
import br.dev.guisleri.agendaarena.match.dto.ChangeParticipantPositionRequestDTO;
import br.dev.guisleri.agendaarena.match.dto.ChangeParticipantTeamRequestDTO;
import br.dev.guisleri.agendaarena.match.dto.ChangePixKeyRequestDTO;
import br.dev.guisleri.agendaarena.match.dto.ChangeTeamSettingsRequestDTO;
import br.dev.guisleri.agendaarena.match.dto.ChangeTeamsRequestDTO;
import br.dev.guisleri.agendaarena.match.dto.MatchResponseDTO;
import br.dev.guisleri.agendaarena.match.dto.MatchSummaryResponseDTO;
import br.dev.guisleri.agendaarena.match.model.MatchTeam;
import br.dev.guisleri.agendaarena.match.service.MatchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestão das minhas partidas pelo id, qualquer que seja a origem. O organizador vem do token;
 * partida de outra pessoa responde 404. As alterações devolvem a partida inteira, porque mexer em
 * um participante muda a cota de todos. A criação fica na origem
 * ({@link MeReservationMatchController}).
 */
@RestController
@RequestMapping("/me/matches/{matchId}")
public class MeMatchController {

    private final MatchService matchService;

    public MeMatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @GetMapping
    public ResponseEntity<MatchResponseDTO> findMyMatch(
            @PathVariable Long matchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(MatchResponseDTO.fromDetails(
                matchService.findMyMatch(AuthenticatedUsers.userId(authentication), matchId)
        ));
    }

    @PostMapping("/close")
    public ResponseEntity<MatchResponseDTO> closeMatch(
            @PathVariable Long matchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(MatchResponseDTO.fromDetails(
                matchService.closeMatch(AuthenticatedUsers.userId(authentication), matchId)
        ));
    }

    @PostMapping("/reopen")
    public ResponseEntity<MatchResponseDTO> reopenMatch(
            @PathVariable Long matchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(MatchResponseDTO.fromDetails(
                matchService.reopenMatch(AuthenticatedUsers.userId(authentication), matchId)
        ));
    }

    /**
     * Sorteia (ou refaz) os 2 times com todos os inscritos, substituindo a divisão atual.
     */
    @PostMapping("/team-draw")
    public ResponseEntity<MatchResponseDTO> drawTeams(
            @PathVariable Long matchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(MatchResponseDTO.fromDetails(
                matchService.drawTeams(AuthenticatedUsers.userId(authentication), matchId)
        ));
    }

    /**
     * Nome e cor dos dois times numa chamada só (ex.: trocar as cores entre eles).
     */
    @PutMapping("/teams")
    public ResponseEntity<MatchResponseDTO> changeTeamsSettings(
            @PathVariable Long matchId,
            @Valid @RequestBody ChangeTeamsRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(MatchResponseDTO.fromDetails(
                matchService.changeTeamsSettings(
                        AuthenticatedUsers.userId(authentication),
                        matchId,
                        request.teamA().name(),
                        request.teamA().shirtColor(),
                        request.teamB().name(),
                        request.teamB().shirtColor()
                )
        ));
    }

    /**
     * Nome e cor de camiseta do time A ou B.
     */
    @PutMapping("/teams/{team}")
    public ResponseEntity<MatchResponseDTO> changeTeamSettings(
            @PathVariable Long matchId,
            @PathVariable MatchTeam team,
            @Valid @RequestBody ChangeTeamSettingsRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(MatchResponseDTO.fromDetails(
                matchService.changeTeamSettings(
                        AuthenticatedUsers.userId(authentication),
                        matchId,
                        team,
                        request.name(),
                        request.shirtColor()
                )
        ));
    }

    /**
     * Montagem manual: {@code {"team": "A"}}, {@code "B"} ou {@code null} (sem time).
     */
    @PutMapping("/participants/{participantId}/team")
    public ResponseEntity<MatchResponseDTO> changeParticipantTeam(
            @PathVariable Long matchId,
            @PathVariable Long participantId,
            @RequestBody ChangeParticipantTeamRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(MatchResponseDTO.fromDetails(
                matchService.changeParticipantTeam(
                        AuthenticatedUsers.userId(authentication),
                        matchId,
                        participantId,
                        request.team()
                )
        ));
    }

    @GetMapping("/summary")
    public ResponseEntity<MatchSummaryResponseDTO> findMySummary(
            @PathVariable Long matchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(MatchSummaryResponseDTO.fromDetails(
                matchService.findMyMatch(AuthenticatedUsers.userId(authentication), matchId)
        ));
    }

    @PutMapping("/pix-key")
    public ResponseEntity<MatchResponseDTO> changePixKey(
            @PathVariable Long matchId,
            @Valid @RequestBody ChangePixKeyRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(MatchResponseDTO.fromDetails(
                matchService.changePixKey(
                        AuthenticatedUsers.userId(authentication),
                        matchId,
                        request.pixKey()
                )
        ));
    }

    @PatchMapping("/participants/{participantId}")
    public ResponseEntity<MatchResponseDTO> changeParticipantPosition(
            @PathVariable Long matchId,
            @PathVariable Long participantId,
            @Valid @RequestBody ChangeParticipantPositionRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(MatchResponseDTO.fromDetails(
                matchService.changeParticipantPosition(
                        AuthenticatedUsers.userId(authentication),
                        matchId,
                        participantId,
                        request.position()
                )
        ));
    }

    @PutMapping("/participants/{participantId}/pays-share")
    public ResponseEntity<MatchResponseDTO> changeParticipantPaysShare(
            @PathVariable Long matchId,
            @PathVariable Long participantId,
            @Valid @RequestBody ChangeParticipantPaysShareRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(MatchResponseDTO.fromDetails(
                matchService.changeParticipantPaysShare(
                        AuthenticatedUsers.userId(authentication),
                        matchId,
                        participantId,
                        request.paysShare()
                )
        ));
    }

    @PutMapping("/participants/{participantId}/payment")
    public ResponseEntity<MatchResponseDTO> changeParticipantPayment(
            @PathVariable Long matchId,
            @PathVariable Long participantId,
            @Valid @RequestBody ChangeParticipantPaymentRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(MatchResponseDTO.fromDetails(
                matchService.changeParticipantPayment(
                        AuthenticatedUsers.userId(authentication),
                        matchId,
                        participantId,
                        request.paid()
                )
        ));
    }

    @DeleteMapping("/participants/{participantId}")
    public ResponseEntity<Void> removeParticipant(
            @PathVariable Long matchId,
            @PathVariable Long participantId,
            Authentication authentication
    ) {
        matchService.removeParticipant(
                AuthenticatedUsers.userId(authentication),
                matchId,
                participantId
        );

        return ResponseEntity.noContent().build();
    }
}
