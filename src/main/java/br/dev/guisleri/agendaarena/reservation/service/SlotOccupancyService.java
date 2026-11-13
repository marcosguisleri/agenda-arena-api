package br.dev.guisleri.agendaarena.reservation.service;

import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.reservation.model.ReservationStatus;
import br.dev.guisleri.agendaarena.reservation.repository.RecurringReservationRepository;
import br.dev.guisleri.agendaarena.reservation.repository.ReservationRepository;
import br.dev.guisleri.agendaarena.reservation.repository.SlotBlockRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SlotOccupancyService {

    private final ReservationRepository reservationRepository;
    private final RecurringReservationRepository recurringReservationRepository;
    private final SlotBlockRepository slotBlockRepository;

    public SlotOccupancyService(
            ReservationRepository reservationRepository,
            RecurringReservationRepository recurringReservationRepository,
            SlotBlockRepository slotBlockRepository
    ) {
        this.reservationRepository = reservationRepository;
        this.recurringReservationRepository = recurringReservationRepository;
        this.slotBlockRepository = slotBlockRepository;
    }

    public boolean isOccupied(
            Long scheduleSlotId,
            LocalDate date
    ) {
        return reservationRepository
                .existsByScheduleSlotIdAndReservationDateAndStatusIn(
                        scheduleSlotId,
                        date,
                        ReservationStatus.OCCUPYING
                ) || recurringReservationRepository
                .existsCoveringDate(
                        scheduleSlotId,
                        date
                ) || isBlocked(
                        scheduleSlotId,
                        date
                );
    }

    public boolean isBlocked(
            Long scheduleSlotId,
            LocalDate date
    ) {
        return slotBlockRepository
                .existsByScheduleSlotIdAndBlockDate(
                        scheduleSlotId,
                        date
                );
    }

    public Set<Long> findOccupiedScheduleSlotIds(
            Long sportFieldId,
            LocalDate date
    ) {
        List<Reservation> reservations =
                reservationRepository
                        .findAllByScheduleSlotSportFieldIdAndReservationDateAndStatusIn(
                                sportFieldId,
                                date,
                                ReservationStatus.OCCUPYING
                        );

        Set<Long> occupiedSlotIds =
                reservations.stream()
                        .map(reservation -> reservation.getScheduleSlot().getId())
                        .collect(Collectors.toSet());

        List<Long> recurringReservationSlotIds =
                recurringReservationRepository
                        .findOccupiedScheduleSlotIds(
                                sportFieldId,
                                date
                        );

        occupiedSlotIds.addAll(recurringReservationSlotIds);

        occupiedSlotIds.addAll(
                slotBlockRepository.findBlockedScheduleSlotIds(
                        sportFieldId,
                        date
                )
        );

        return occupiedSlotIds;
    }

}
