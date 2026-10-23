package br.dev.guisleri.agendaarena.reservation.service;

import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.reservation.exception.ReservationAlreadyExistsException;
import br.dev.guisleri.agendaarena.reservation.exception.ReservationNotAllowedException;
import br.dev.guisleri.agendaarena.reservation.model.ReservationStatus;
import br.dev.guisleri.agendaarena.reservation.repository.ReservationRepository;
import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import br.dev.guisleri.agendaarena.schedule.service.ScheduleSlotService;
import br.dev.guisleri.agendaarena.user.entity.User;
import br.dev.guisleri.agendaarena.user.service.UserService;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

@Service
public class ReservationService {

    private final UserService userService;
    private final ScheduleSlotService scheduleSlotService;
    private final ReservationRepository reservationRepository;
    private final Clock clock;

    public ReservationService(UserService userService, ScheduleSlotService scheduleSlotService, ReservationRepository reservationRepository, Clock clock) {
        this.userService = userService;
        this.scheduleSlotService = scheduleSlotService;
        this.reservationRepository = reservationRepository;
        this.clock = clock;
    }

    @Transactional
    public Reservation createReservation(
            Long establishmentId,
            Long sportFieldId,
            Long scheduleSlotId,
            Long organizerId,
            LocalDate reservationDate
    ) {

        ZonedDateTime now = ZonedDateTime.now(clock);
        LocalDate currentDate = now.toLocalDate();
        LocalTime currentTime = now.toLocalTime();

        if (reservationDate.isBefore(currentDate))
            throw new ReservationNotAllowedException("Reservation date cannot be in the past");

        User organizer = userService.findUserById(organizerId);

        if (!organizer.isActive())
            throw new ReservationNotAllowedException("Organizer is not active");

        ScheduleSlot scheduleSlot =
                scheduleSlotService.findBookableScheduleSlot(
                        establishmentId,
                        sportFieldId,
                        scheduleSlotId
                );

        if (!reservationDate.getDayOfWeek().equals(scheduleSlot.getDayOfWeek())) {
            throw new ReservationNotAllowedException(
                    "Reservation date does not match schedule slot day of week"
            );
        }

        if (reservationDate.equals(currentDate)) {
            if (!scheduleSlot.getStartTime().isAfter(currentTime))
                throw new ReservationNotAllowedException("Reservation time cannot be in the past");
        }

        boolean reservationExists = reservationRepository
                .existsByScheduleSlotIdAndReservationDateAndStatus(
                        scheduleSlotId,
                        reservationDate,
                        ReservationStatus.CONFIRMED
                );

        if (reservationExists) {
            throw new ReservationAlreadyExistsException("Schedule slot is already reserved for this date");
        }

        try {
            return reservationRepository.saveAndFlush(
                    new Reservation(organizer, scheduleSlot, reservationDate)
            );
        } catch (DataIntegrityViolationException exception) {
            if (exception.getMessage() != null
                    && exception.getMessage().contains("idx_unique_active_reservation")) {
                throw new ReservationAlreadyExistsException(
                        "Schedule slot is already reserved for this date"
                );
            }

            throw exception;
        }

    }

}
