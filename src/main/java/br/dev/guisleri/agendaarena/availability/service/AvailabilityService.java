package br.dev.guisleri.agendaarena.availability.service;

import br.dev.guisleri.agendaarena.availability.dto.AvailabilityResponseDTO;
import br.dev.guisleri.agendaarena.availability.exception.AvailabilityNotAllowedException;
import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.service.EstablishmentService;
import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.reservation.model.ReservationStatus;
import br.dev.guisleri.agendaarena.reservation.repository.ReservationRepository;
import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import br.dev.guisleri.agendaarena.schedule.repository.ScheduleSlotRepository;
import br.dev.guisleri.agendaarena.sportfield.entity.SportField;
import br.dev.guisleri.agendaarena.sportfield.service.SportFieldService;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AvailabilityService {

    private final EstablishmentService establishmentService;
    private final SportFieldService sportFieldService;
    private final ScheduleSlotRepository scheduleSlotRepository;
    private final ReservationRepository reservationRepository;
    private final Clock clock;

    public AvailabilityService(EstablishmentService establishmentService, SportFieldService sportFieldService, ScheduleSlotRepository scheduleSlotRepository, ReservationRepository reservationRepository, Clock clock) {
        this.establishmentService = establishmentService;
        this.sportFieldService = sportFieldService;
        this.scheduleSlotRepository = scheduleSlotRepository;
        this.reservationRepository = reservationRepository;
        this.clock = clock;
    }

    public List<AvailabilityResponseDTO> findAvailability(
            Long establishmentId,
            Long sportFieldId,
            LocalDate date
    ) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        LocalDate currentDate = now.toLocalDate();
        LocalTime currentTime = now.toLocalTime();

        if (date.isBefore(currentDate)) {
            throw new AvailabilityNotAllowedException(
                    "Availability date cannot be in the past"
            );
        }

        DayOfWeek dayOfWeek = date.getDayOfWeek();

        Establishment establishment =
                establishmentService.findEstablishmentById(establishmentId);

        if (!establishment.isActive()) {
            throw new AvailabilityNotAllowedException(
                    "Establishment is not active."
            );
        }

        SportField sportField =
                sportFieldService.findSportFieldById(
                        establishmentId,
                        sportFieldId
                );

        if (!sportField.isActive()) {
            throw new AvailabilityNotAllowedException(
                    "Sport field is not active."
            );
        }

        List<ScheduleSlot> scheduleSlots =
                scheduleSlotRepository
                        .findAllBySportFieldIdAndDayOfWeekAndActiveTrueOrderByStartTime(
                                sportFieldId,
                                dayOfWeek
                        );

        if (date.equals(currentDate)) {
            scheduleSlots = scheduleSlots.stream()
                    .filter(scheduleSlot ->
                            scheduleSlot.getStartTime().isAfter(currentTime)
                    )
                    .toList();
        }

        List<Reservation> reservations =
                reservationRepository.findAllByScheduleSlotSportFieldIdAndReservationDateAndStatus(
                        sportFieldId,
                        date,
                        ReservationStatus.CONFIRMED
                );

        Set<Long> reservedSlotIds = reservations.stream()
                .map(reservation -> reservation.getScheduleSlot().getId())
                .collect(Collectors.toSet());

        return scheduleSlots.stream()
                .map(scheduleSlot -> {
                    boolean available =
                            !reservedSlotIds.contains(scheduleSlot.getId());

                    return new AvailabilityResponseDTO(
                            scheduleSlot.getId(),
                            date,
                            scheduleSlot.getStartTime(),
                            scheduleSlot.getEndTime(),
                            scheduleSlot.getPrice(),
                            available
                    );
                })
                .toList();
    }
}
