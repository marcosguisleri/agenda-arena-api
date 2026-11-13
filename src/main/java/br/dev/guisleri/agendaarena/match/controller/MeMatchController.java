package br.dev.guisleri.agendaarena.match.controller;

import br.dev.guisleri.agendaarena.common.security.AuthenticatedUsers;
import br.dev.guisleri.agendaarena.match.dto.ChangeParticipantPaymentRequestDTO;
import br.dev.guisleri.agendaarena.match.dto.ChangeParticipantPaysShareRequestDTO;
import br.dev.guisleri.agendaarena.match.dto.ChangeParticipantPositionRequestDTO;
import br.dev.guisleri.agendaarena.match.dto.ChangePixKeyRequestDTO;
import br.dev.guisleri.agendaarena.match.dto.MatchResponseDTO;
import br.dev.guisleri.agendaarena.match.service.MatchCreationResult;
import br.dev.guisleri.agendaarena.match.service.MatchService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
 * Partida das minhas reservas. O organizador vem do token; reserva de outra pessoa responde 404.
 * As alterações devolvem a partida inteira, porque mexer em um participante muda a cota de todos.
 */
@RestController
@RequestMapping("/me/reservations/{reservationId}/match")
public class MeMatchController {

    private final MatchService matchService;

    public MeMatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    /**
     * 201 ao criar; 200 com a mesma partida se ela já existia.
     */
    @PostMapping
    public ResponseEntity<MatchResponseDTO> createMatch(
            @PathVariable Long reservationId,
            Authentication authentication
    ) {
        MatchCreationResult result = matchService.createMatch(
                AuthenticatedUsers.userId(authentication),
                reservationId
        );

        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(MatchResponseDTO.fromDetails(result.details()));
    }

    @GetMapping
    public ResponseEntity<MatchResponseDTO> findMyMatch(
            @PathVariable Long reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(MatchResponseDTO.fromDetails(
                matchService.findMyMatch(AuthenticatedUsers.userId(authentication), reservationId)
        ));
    }

    @PostMapping("/close")
    public ResponseEntity<MatchResponseDTO> closeMatch(
            @PathVariable Long reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(MatchResponseDTO.fromDetails(
                matchService.closeMatch(AuthenticatedUsers.userId(authentication), reservationId)
        ));
    }

    @PostMapping("/reopen")
    public ResponseEntity<MatchResponseDTO> reopenMatch(
            @PathVariable Long reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(MatchResponseDTO.fromDetails(
                matchService.reopenMatch(AuthenticatedUsers.userId(authentication), reservationId)
        ));
    }

    @PutMapping("/pix-key")
    public ResponseEntity<MatchResponseDTO> changePixKey(
            @PathVariable Long reservationId,
            @Valid @RequestBody ChangePixKeyRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(MatchResponseDTO.fromDetails(
                matchService.changePixKey(
                        AuthenticatedUsers.userId(authentication),
                        reservationId,
                        request.pixKey()
                )
        ));
    }

    @PatchMapping("/participants/{participantId}")
    public ResponseEntity<MatchResponseDTO> changeParticipantPosition(
            @PathVariable Long reservationId,
            @PathVariable Long participantId,
            @Valid @RequestBody ChangeParticipantPositionRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(MatchResponseDTO.fromDetails(
                matchService.changeParticipantPosition(
                        AuthenticatedUsers.userId(authentication),
                        reservationId,
                        participantId,
                        request.position()
                )
        ));
    }

    @PutMapping("/participants/{participantId}/pays-share")
    public ResponseEntity<MatchResponseDTO> changeParticipantPaysShare(
            @PathVariable Long reservationId,
            @PathVariable Long participantId,
            @Valid @RequestBody ChangeParticipantPaysShareRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(MatchResponseDTO.fromDetails(
                matchService.changeParticipantPaysShare(
                        AuthenticatedUsers.userId(authentication),
                        reservationId,
                        participantId,
                        request.paysShare()
                )
        ));
    }

    @PutMapping("/participants/{participantId}/payment")
    public ResponseEntity<MatchResponseDTO> changeParticipantPayment(
            @PathVariable Long reservationId,
            @PathVariable Long participantId,
            @Valid @RequestBody ChangeParticipantPaymentRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(MatchResponseDTO.fromDetails(
                matchService.changeParticipantPayment(
                        AuthenticatedUsers.userId(authentication),
                        reservationId,
                        participantId,
                        request.paid()
                )
        ));
    }

    @DeleteMapping("/participants/{participantId}")
    public ResponseEntity<Void> removeParticipant(
            @PathVariable Long reservationId,
            @PathVariable Long participantId,
            Authentication authentication
    ) {
        matchService.removeParticipant(
                AuthenticatedUsers.userId(authentication),
                reservationId,
                participantId
        );

        return ResponseEntity.noContent().build();
    }
}
