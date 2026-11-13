package br.dev.guisleri.agendaarena.reservation.controller;

import br.dev.guisleri.agendaarena.reservation.dto.CreateRecurringReservationReleaseRequestDTO;
import br.dev.guisleri.agendaarena.reservation.dto.CreateRecurringReservationRequestDTO;
import br.dev.guisleri.agendaarena.reservation.dto.LinkRecurringReservationOrganizerRequestDTO;
import br.dev.guisleri.agendaarena.reservation.dto.RecurringReservationReleaseResponseDTO;
import br.dev.guisleri.agendaarena.reservation.dto.RecurringReservationResponseDTO;
import br.dev.guisleri.agendaarena.reservation.entity.RecurringReservation;
import br.dev.guisleri.agendaarena.reservation.entity.RecurringReservationRelease;
import br.dev.guisleri.agendaarena.reservation.service.RecurringReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(
        "/establishments/{establishmentId}/sports-fields/{sportFieldId}/schedule-slots/{scheduleSlotId}/recurring-reservations"
)
public class RecurringReservationController {

    private final RecurringReservationService recurringReservationService;

    public RecurringReservationController(RecurringReservationService recurringReservationService) {
        this.recurringReservationService = recurringReservationService;
    }

    @PostMapping
    public ResponseEntity<RecurringReservationResponseDTO> createRecurringReservation(
            @PathVariable Long establishmentId,
            @PathVariable Long sportFieldId,
            @PathVariable Long scheduleSlotId,
            @Valid @RequestBody CreateRecurringReservationRequestDTO requestDTO
    ) {

        RecurringReservation recurringReservation =
                recurringReservationService.createRecurringReservation(
                        establishmentId,
                        sportFieldId,
                        scheduleSlotId,
                        requestDTO.organizerId(),
                        requestDTO.responsibleName(),
                        requestDTO.responsiblePhone(),
                        requestDTO.startDate(),
                        requestDTO.endDate()
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(RecurringReservationResponseDTO.fromEntity(recurringReservation));

    }

    @PostMapping("/{recurringReservationId}/releases")
    public ResponseEntity<RecurringReservationReleaseResponseDTO> releaseOccurrence(
            @PathVariable Long establishmentId,
            @PathVariable Long sportFieldId,
            @PathVariable Long scheduleSlotId,
            @PathVariable Long recurringReservationId,
            @Valid @RequestBody CreateRecurringReservationReleaseRequestDTO requestDTO
    ) {

        RecurringReservationRelease release =
                recurringReservationService.releaseOccurrence(
                        establishmentId,
                        sportFieldId,
                        scheduleSlotId,
                        recurringReservationId,
                        requestDTO.occurrenceDate()
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(RecurringReservationReleaseResponseDTO.fromEntity(release));

    }

    @PatchMapping("/{recurringReservationId}")
    public ResponseEntity<RecurringReservationResponseDTO> linkOrganizer(
            @PathVariable Long establishmentId,
            @PathVariable Long sportFieldId,
            @PathVariable Long scheduleSlotId,
            @PathVariable Long recurringReservationId,
            @Valid @RequestBody LinkRecurringReservationOrganizerRequestDTO requestDTO
    ) {

        return ResponseEntity.ok(
                RecurringReservationResponseDTO.fromEntity(
                        recurringReservationService.linkOrganizer(
                                establishmentId,
                                sportFieldId,
                                scheduleSlotId,
                                recurringReservationId,
                                requestDTO.organizerId()
                        )
                )
        );

    }

}
