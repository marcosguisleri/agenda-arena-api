package br.dev.guisleri.agendaarena.reservation.service;

import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.reservation.model.ReservationStatus;
import br.dev.guisleri.agendaarena.reservation.repository.RecurringReservationRepository;
import br.dev.guisleri.agendaarena.reservation.repository.ReservationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SlotOccupancyService {

    private final ReservationRepository reservationRepository;
    private final RecurringReservationRepository recurringReservationRepository;

    public SlotOccupancyService(ReservationRepository reservationRepository, RecurringReservationRepository recurringReservationRepository) {
        this.reservationRepository = reservationRepository;
        this.recurringReservationRepository = recurringReservationRepository;
    }

    public boolean isOccupied(
            Long scheduleSlotId,
            LocalDate date
    ) {
        return reservationRepository
                .existsByScheduleSlotIdAndReservationDateAndStatus(
                        scheduleSlotId,
                        date,
                        ReservationStatus.CONFIRMED
                ) || recurringReservationRepository
                .existsCoveringDate(
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
                        .findAllByScheduleSlotSportFieldIdAndReservationDateAndStatus(
                                sportFieldId,
                                date,
                                ReservationStatus.CONFIRMED
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

        return occupiedSlotIds;
    }

}
