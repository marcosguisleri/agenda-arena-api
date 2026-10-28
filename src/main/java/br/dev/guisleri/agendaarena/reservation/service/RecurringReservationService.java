package br.dev.guisleri.agendaarena.reservation.service;

import br.dev.guisleri.agendaarena.reservation.entity.RecurringReservation;
import br.dev.guisleri.agendaarena.reservation.exception.RecurringReservationConflictException;
import br.dev.guisleri.agendaarena.reservation.model.ReservationStatus;
import br.dev.guisleri.agendaarena.reservation.repository.RecurringReservationRepository;
import br.dev.guisleri.agendaarena.reservation.repository.ReservationRepository;
import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import br.dev.guisleri.agendaarena.schedule.service.ScheduleSlotService;
import br.dev.guisleri.agendaarena.user.entity.User;
import br.dev.guisleri.agendaarena.user.service.UserService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class RecurringReservationService {

    private final ReservationRepository reservationRepository;
    private final RecurringReservationRepository recurringReservationRepository;
    private final ScheduleSlotService scheduleSlotService;
    private final UserService userService;

    public RecurringReservationService(ReservationRepository reservationRepository, RecurringReservationRepository recurringReservationRepository, ScheduleSlotService scheduleSlotService, UserService userService) {
        this.reservationRepository = reservationRepository;
        this.recurringReservationRepository = recurringReservationRepository;
        this.scheduleSlotService = scheduleSlotService;
        this.userService = userService;
    }

    @Transactional
    public RecurringReservation createRecurringReservation(
            Long establishmentId,
            Long sportFieldId,
            Long scheduleSlotId,
            Long userId,
            String responsibleName,
            String responsiblePhone,
            LocalDate startDate,
            LocalDate endDate
    ) {
        User organizer = userId != null
                ? userService.findUserById(userId)
                : null;

        ScheduleSlot scheduleSlot =
                scheduleSlotService.findBookableScheduleSlotForUpdate(
                        establishmentId,
                        sportFieldId,
                        scheduleSlotId
                );

        RecurringReservation recurringReservation = new RecurringReservation(
                scheduleSlot,
                organizer,
                responsibleName,
                responsiblePhone,
                startDate,
                endDate
        );

        boolean hasConflict;

        if (endDate == null) {
            hasConflict = reservationRepository.existsByScheduleSlotIdAndStatusAndReservationDateGreaterThanEqual(
                    scheduleSlotId,
                    ReservationStatus.CONFIRMED,
                    startDate
            );
        } else {
            hasConflict = reservationRepository.existsByScheduleSlotIdAndStatusAndReservationDateBetween(
                    scheduleSlotId,
                    ReservationStatus.CONFIRMED,
                    startDate,
                    endDate
            );
        }

        if (hasConflict) {
            throw new RecurringReservationConflictException("There are already confirmed reservations for that period, which is causing a conflict.");
        }

        boolean recurringConflict =
                recurringReservationRepository.existsOverlapping(
                        scheduleSlotId,
                        startDate,
                        endDate
                );

        if (recurringConflict) {
            throw new RecurringReservationConflictException(
                    "There is already a recurring reservation for that period."
            );
        }

        return saveRecurringReservation(recurringReservation);
    }

    private RecurringReservation saveRecurringReservation(
            RecurringReservation recurringReservation
    ) {
        try {
            return recurringReservationRepository.saveAndFlush(
                    recurringReservation
            );

        } catch (DataIntegrityViolationException exception) {

            String message =
                    exception.getMostSpecificCause().getMessage();

            if (message != null
                    && message.contains(
                    "ex_recurring_reservations_no_overlap"
            )) {

                throw new RecurringReservationConflictException(
                        "There is already a recurring reservation for that period."
                );
            }

            throw exception;
        }
    }

}
