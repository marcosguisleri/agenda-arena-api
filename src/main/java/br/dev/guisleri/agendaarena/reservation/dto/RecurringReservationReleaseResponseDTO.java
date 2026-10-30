package br.dev.guisleri.agendaarena.reservation.dto;

import br.dev.guisleri.agendaarena.reservation.entity.RecurringReservationRelease;

import java.time.Instant;
import java.time.LocalDate;

public record RecurringReservationReleaseResponseDTO(
        Long id,
        Long recurringReservationId,
        LocalDate occurrenceDate,
        Instant releasedAt
) {
    public static RecurringReservationReleaseResponseDTO fromEntity(
            RecurringReservationRelease release
    ) {
        return new RecurringReservationReleaseResponseDTO(
                release.getId(),
                release.getRecurringReservation().getId(),
                release.getOccurrenceDate(),
                release.getReleasedAt()
        );
    }
}
