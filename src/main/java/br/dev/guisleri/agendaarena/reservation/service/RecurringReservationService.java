package br.dev.guisleri.agendaarena.reservation.service;

import br.dev.guisleri.agendaarena.reservation.entity.RecurringReservation;
import br.dev.guisleri.agendaarena.reservation.entity.RecurringReservationRelease;
import br.dev.guisleri.agendaarena.reservation.exception.RecurringReservationConflictException;
import br.dev.guisleri.agendaarena.reservation.exception.RecurringReservationNotAllowedException;
import br.dev.guisleri.agendaarena.reservation.exception.RecurringReservationNotFoundException;
import br.dev.guisleri.agendaarena.reservation.model.ReservationStatus;
import br.dev.guisleri.agendaarena.reservation.repository.RecurringReservationReleaseRepository;
import br.dev.guisleri.agendaarena.reservation.repository.RecurringReservationRepository;
import br.dev.guisleri.agendaarena.reservation.repository.ReservationRepository;
import br.dev.guisleri.agendaarena.reservation.repository.SlotBlockRepository;
import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import br.dev.guisleri.agendaarena.schedule.service.ScheduleSlotService;
import br.dev.guisleri.agendaarena.user.entity.User;
import br.dev.guisleri.agendaarena.user.service.UserService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;

@Service
public class RecurringReservationService {

    private final ReservationRepository reservationRepository;
    private final RecurringReservationRepository recurringReservationRepository;
    private final ScheduleSlotService scheduleSlotService;
    private final UserService userService;
    private final RecurringReservationReleaseRepository recurringReservationReleaseRepository;
    private final SlotBlockRepository slotBlockRepository;
    private final Clock clock;

    public RecurringReservationService(
            ReservationRepository reservationRepository,
            RecurringReservationRepository recurringReservationRepository,
            ScheduleSlotService scheduleSlotService,
            UserService userService,
            RecurringReservationReleaseRepository recurringReservationReleaseRepository,
            SlotBlockRepository slotBlockRepository,
            Clock clock
    ) {
        this.reservationRepository = reservationRepository;
        this.recurringReservationRepository = recurringReservationRepository;
        this.scheduleSlotService = scheduleSlotService;
        this.userService = userService;
        this.recurringReservationReleaseRepository = recurringReservationReleaseRepository;
        this.slotBlockRepository = slotBlockRepository;
        this.clock = clock;
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
            hasConflict = reservationRepository.existsByScheduleSlotIdAndStatusInAndReservationDateGreaterThanEqual(
                    scheduleSlotId,
                    ReservationStatus.OCCUPYING,
                    startDate
            );
        } else {
            hasConflict = reservationRepository.existsByScheduleSlotIdAndStatusInAndReservationDateBetween(
                    scheduleSlotId,
                    ReservationStatus.OCCUPYING,
                    startDate,
                    endDate
            );
        }

        if (hasConflict) {
            throw new RecurringReservationConflictException("There are already confirmed reservations for that period, which is causing a conflict.");
        }

        boolean blockConflict;

        if (endDate == null) {
            blockConflict = slotBlockRepository.existsByScheduleSlotIdAndBlockDateGreaterThanEqual(
                    scheduleSlotId,
                    startDate
            );
        } else {
            blockConflict = slotBlockRepository.existsByScheduleSlotIdAndBlockDateBetween(
                    scheduleSlotId,
                    startDate,
                    endDate
            );
        }

        if (blockConflict) {
            throw new RecurringReservationConflictException(
                    "There are blocked dates in that period, which is causing a conflict."
            );
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

    /**
     * Libera uma única ocorrência de uma recorrência, sem excluir a série.
     * Segue o mesmo protocolo das demais reservas: lock pessimista do slot primeiro, validações depois.
     * Assim, liberar e reservar a mesma data são serializados e nunca disputam o mesmo estado.
     */
    @Transactional
    public RecurringReservationRelease releaseOccurrence(
            Long establishmentId,
            Long sportFieldId,
            Long scheduleSlotId,
            Long recurringReservationId,
            LocalDate occurrenceDate
    ) {
        ScheduleSlot scheduleSlot =
                scheduleSlotService.findBookableScheduleSlotForUpdate(
                        establishmentId,
                        sportFieldId,
                        scheduleSlotId
                );

        RecurringReservation recurringReservation =
                recurringReservationRepository
                        .findByIdAndScheduleSlotId(
                                recurringReservationId,
                                scheduleSlotId
                        )
                        .orElseThrow(() ->
                                new RecurringReservationNotFoundException(
                                        "Recurring reservation with id "
                                                + recurringReservationId
                                                + " not found for schedule slot "
                                                + scheduleSlotId
                                )
                        );

        validateOccurrenceCanBeReleased(
                recurringReservation,
                scheduleSlot,
                occurrenceDate
        );

        boolean alreadyReleased =
                recurringReservationReleaseRepository
                        .existsByRecurringReservationIdAndOccurrenceDate(
                                recurringReservationId,
                                occurrenceDate
                        );

        if (alreadyReleased) {
            throw new RecurringReservationConflictException(
                    "This occurrence has already been released."
            );
        }

        return saveRelease(
                new RecurringReservationRelease(
                        recurringReservation,
                        occurrenceDate,
                        Instant.now(clock)
                )
        );
    }

    private void validateOccurrenceCanBeReleased(
            RecurringReservation recurringReservation,
            ScheduleSlot scheduleSlot,
            LocalDate occurrenceDate
    ) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        LocalDate currentDate = now.toLocalDate();

        if (occurrenceDate.isBefore(currentDate)) {
            throw new RecurringReservationNotAllowedException(
                    "Occurrence date cannot be in the past"
            );
        }

        if (!occurrenceDate.getDayOfWeek().equals(scheduleSlot.getDayOfWeek())) {
            throw new RecurringReservationNotAllowedException(
                    "Occurrence date does not match schedule slot day of week"
            );
        }

        boolean beforeStart =
                occurrenceDate.isBefore(recurringReservation.getStartDate());

        boolean afterEnd =
                recurringReservation.getEndDate() != null
                        && occurrenceDate.isAfter(recurringReservation.getEndDate());

        if (beforeStart || afterEnd) {
            throw new RecurringReservationNotAllowedException(
                    "Occurrence date is outside the recurring reservation period"
            );
        }

        if (occurrenceDate.equals(currentDate)
                && !scheduleSlot.getStartTime().isAfter(now.toLocalTime())) {
            throw new RecurringReservationNotAllowedException(
                    "Occurrence time cannot be in the past"
            );
        }
    }

    private RecurringReservationRelease saveRelease(
            RecurringReservationRelease release
    ) {
        try {
            return recurringReservationReleaseRepository.saveAndFlush(release);

        } catch (DataIntegrityViolationException exception) {

            String message =
                    exception.getMostSpecificCause().getMessage();

            if (message != null
                    && message.contains(
                    "uk_recurring_reservations_releases_reservation_date"
            )) {

                throw new RecurringReservationConflictException(
                        "This occurrence has already been released."
                );
            }

            throw exception;
        }
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
