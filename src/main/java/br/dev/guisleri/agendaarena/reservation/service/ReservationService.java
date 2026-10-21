package br.dev.guisleri.agendaarena.reservation.service;

import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.reservation.exception.ReservationAlreadyExistsException;
import br.dev.guisleri.agendaarena.reservation.exception.ReservationNotAllowedException;
import br.dev.guisleri.agendaarena.reservation.repository.ReservationRepository;
import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import br.dev.guisleri.agendaarena.schedule.service.ScheduleSlotService;
import br.dev.guisleri.agendaarena.user.entity.User;
import br.dev.guisleri.agendaarena.user.service.UserService;
import org.springframework.transaction.annotation.Transactional;
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
    private final SlotOccupancyService slotOccupancyService;
    private final ReservationRepository reservationRepository;
    private final Clock clock;

    public ReservationService(
            UserService userService,
            ScheduleSlotService scheduleSlotService, SlotOccupancyService slotOccupancyService,
            ReservationRepository reservationRepository,
            Clock clock
    ) {
        this.userService = userService;
        this.scheduleSlotService = scheduleSlotService;
        this.slotOccupancyService = slotOccupancyService;
        this.reservationRepository = reservationRepository;
        this.clock = clock;
    }

    @Transactional
    public Reservation createOnlineReservation(
            Long establishmentId,
            Long sportFieldId,
            Long scheduleSlotId,
            Long organizerId,
            LocalDate reservationDate
    ) {

        User organizer = userService.findUserById(organizerId);

        if (!organizer.isActive()) {
            throw new ReservationNotAllowedException(
                    "Organizer is not active"
            );
        }

        ScheduleSlot scheduleSlot =
                findReservableScheduleSlot(
                        establishmentId,
                        sportFieldId,
                        scheduleSlotId,
                        reservationDate
                );

        Reservation reservation =
                Reservation.online(
                        organizer,
                        scheduleSlot,
                        reservationDate
                );

        return saveReservation(reservation);
    }

    @Transactional
    public Reservation createManualReservation(
            Long establishmentId,
            Long sportFieldId,
            Long scheduleSlotId,
            LocalDate reservationDate,
            String customerName,
            String customerPhone
    ) {

        String normalizedName = customerName.trim();

        String normalizedPhone =
                customerPhone == null || customerPhone.isBlank()
                        ? null
                        : customerPhone.replaceAll("\\D", "");

        ScheduleSlot scheduleSlot =
                findReservableScheduleSlot(
                        establishmentId,
                        sportFieldId,
                        scheduleSlotId,
                        reservationDate
                );

        Reservation reservation =
                Reservation.manual(
                        scheduleSlot,
                        reservationDate,
                        normalizedName,
                        normalizedPhone
                );

        return saveReservation(reservation);
    }

    private ScheduleSlot findReservableScheduleSlot(
            Long establishmentId,
            Long sportFieldId,
            Long scheduleSlotId,
            LocalDate reservationDate
    ) {

        ZonedDateTime now = ZonedDateTime.now(clock);
        LocalDate currentDate = now.toLocalDate();
        LocalTime currentTime = now.toLocalTime();

        if (reservationDate.isBefore(currentDate)) {
            throw new ReservationNotAllowedException(
                    "Reservation date cannot be in the past"
            );
        }

        ScheduleSlot scheduleSlot =
                scheduleSlotService.findBookableScheduleSlotForUpdate(
                        establishmentId,
                        sportFieldId,
                        scheduleSlotId
                );

        if (!reservationDate
                .getDayOfWeek()
                .equals(scheduleSlot.getDayOfWeek())) {

            throw new ReservationNotAllowedException(
                    "Reservation date does not match schedule slot day of week"
            );
        }

        if (reservationDate.equals(currentDate)
                && !scheduleSlot.getStartTime().isAfter(currentTime)) {

            throw new ReservationNotAllowedException(
                    "Reservation time cannot be in the past"
            );
        }

        boolean occupied =
                slotOccupancyService.isOccupied(
                        scheduleSlotId,
                        reservationDate
                );

        if (occupied) {
            throw new ReservationAlreadyExistsException(
                    "Schedule slot is already reserved for this date"
            );
        }

        return scheduleSlot;
    }

    private Reservation saveReservation(Reservation reservation) {

        try {
            return reservationRepository.saveAndFlush(reservation);

        } catch (DataIntegrityViolationException exception) {

            if (exception.getMessage() != null
                    && exception.getMessage()
                    .contains("idx_unique_active_reservation")) {

                throw new ReservationAlreadyExistsException(
                        "Schedule slot is already reserved for this date"
                );
            }

            throw exception;
        }
    }
}