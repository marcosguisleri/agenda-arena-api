package br.dev.guisleri.agendaarena.reservation.controller;

import br.dev.guisleri.agendaarena.common.exception.ValidationFailedException;
import br.dev.guisleri.agendaarena.common.security.AuthenticatedUsers;
import br.dev.guisleri.agendaarena.reservation.dto.CreateManualReservationRequestDTO;
import br.dev.guisleri.agendaarena.reservation.dto.CreateOnlineReservationRequestDTO;
import br.dev.guisleri.agendaarena.reservation.dto.ReservationResponseDTO;
import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.reservation.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/establishments/{establishmentId}/sports-fields/{sportFieldId}/schedule-slots/{scheduleSlotId}/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ReservationResponseDTO> createOnlineReservation(
            @PathVariable Long establishmentId,
            @PathVariable Long sportFieldId,
            @PathVariable Long scheduleSlotId,
            @Valid @RequestBody CreateOnlineReservationRequestDTO requestDTO,
            Authentication authentication
    ) {
        Reservation reservation =
                reservationService.createOnlineReservation(
                        establishmentId,
                        sportFieldId,
                        scheduleSlotId,
                        resolveOrganizerId(authentication, requestDTO.organizerId()),
                        requestDTO.reservationDate()
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ReservationResponseDTO.fromEntity(reservation));
    }

    @GetMapping("/{reservationId}")
    public ResponseEntity<ReservationResponseDTO> findReservation(
            @PathVariable Long establishmentId,
            @PathVariable Long sportFieldId,
            @PathVariable Long scheduleSlotId,
            @PathVariable Long reservationId
    ) {
        Reservation reservation =
                reservationService.findReservation(
                        establishmentId,
                        sportFieldId,
                        scheduleSlotId,
                        reservationId
                );

        return ResponseEntity.ok(ReservationResponseDTO.fromEntity(reservation));
    }

    @PostMapping("/{reservationId}/cancel")
    public ResponseEntity<ReservationResponseDTO> cancelReservation(
            @PathVariable Long establishmentId,
            @PathVariable Long sportFieldId,
            @PathVariable Long scheduleSlotId,
            @PathVariable Long reservationId,
            @RequestParam(defaultValue = "false") boolean keepBlocked
    ) {
        Reservation reservation =
                reservationService.cancelReservation(
                        establishmentId,
                        sportFieldId,
                        scheduleSlotId,
                        reservationId,
                        keepBlocked
                );

        return ResponseEntity.ok(ReservationResponseDTO.fromEntity(reservation));
    }

    @PostMapping("/{reservationId}/refuse-cancellation")
    public ResponseEntity<ReservationResponseDTO> refuseCancellationRequest(
            @PathVariable Long establishmentId,
            @PathVariable Long sportFieldId,
            @PathVariable Long scheduleSlotId,
            @PathVariable Long reservationId
    ) {
        Reservation reservation =
                reservationService.refuseCancellationRequest(
                        establishmentId,
                        sportFieldId,
                        scheduleSlotId,
                        reservationId
                );

        return ResponseEntity.ok(ReservationResponseDTO.fromEntity(reservation));
    }

    @PostMapping("/{reservationId}/complete")
    public ResponseEntity<ReservationResponseDTO> completeReservation(
            @PathVariable Long establishmentId,
            @PathVariable Long sportFieldId,
            @PathVariable Long scheduleSlotId,
            @PathVariable Long reservationId
    ) {
        Reservation reservation =
                reservationService.completeReservation(
                        establishmentId,
                        sportFieldId,
                        scheduleSlotId,
                        reservationId
                );

        return ResponseEntity.ok(ReservationResponseDTO.fromEntity(reservation));
    }

    @PostMapping("/{reservationId}/no-show")
    public ResponseEntity<ReservationResponseDTO> markReservationAsNoShow(
            @PathVariable Long establishmentId,
            @PathVariable Long sportFieldId,
            @PathVariable Long scheduleSlotId,
            @PathVariable Long reservationId
    ) {
        Reservation reservation =
                reservationService.markReservationAsNoShow(
                        establishmentId,
                        sportFieldId,
                        scheduleSlotId,
                        reservationId
                );

        return ResponseEntity.ok(ReservationResponseDTO.fromEntity(reservation));
    }

    @PostMapping("/manual")
    public ResponseEntity<ReservationResponseDTO> createManualReservation(
            @PathVariable Long establishmentId,
            @PathVariable Long sportFieldId,
            @PathVariable Long scheduleSlotId,
            @Valid @RequestBody CreateManualReservationRequestDTO requestDTO
    ) {
        Reservation reservation =
                reservationService.createManualReservation(
                        establishmentId,
                        sportFieldId,
                        scheduleSlotId,
                        requestDTO.reservationDate(),
                        requestDTO.customerName(),
                        requestDTO.customerPhone()
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ReservationResponseDTO.fromEntity(reservation));
    }

    /**
     * O organizador reserva sempre para si mesmo (o id vem do token; se vier no corpo, tem de ser
     * o dele). Um administrador do estabelecimento reserva em nome de um usuário e, por isso,
     * precisa informar o organizerId.
     */
    private Long resolveOrganizerId(Authentication authentication, Long requestedOrganizerId) {
        if (AuthenticatedUsers.hasRole(authentication, AuthenticatedUsers.ORGANIZER)) {
            Long ownId = AuthenticatedUsers.userId(authentication);

            if (requestedOrganizerId != null && !requestedOrganizerId.equals(ownId)) {
                throw new AccessDeniedException(
                        "Organizer id does not match the authenticated user"
                );
            }

            return ownId;
        }

        if (requestedOrganizerId == null) {
            throw new ValidationFailedException(
                    "organizerId",
                    "Organizer id is required"
            );
        }

        return requestedOrganizerId;
    }

}
