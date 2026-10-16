package br.dev.guisleri.agendaarena.reservation.controller;

import br.dev.guisleri.agendaarena.reservation.dto.CreateManualReservationRequestDTO;
import br.dev.guisleri.agendaarena.reservation.dto.CreateOnlineReservationRequestDTO;
import br.dev.guisleri.agendaarena.reservation.dto.ReservationResponseDTO;
import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.reservation.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
            @Valid @RequestBody CreateOnlineReservationRequestDTO requestDTO
    ) {
        Reservation reservation =
                reservationService.createOnlineReservation(
                        establishmentId,
                        sportFieldId,
                        scheduleSlotId,
                        requestDTO.organizerId(),
                        requestDTO.reservationDate()
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ReservationResponseDTO.fromEntity(reservation));
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

}
