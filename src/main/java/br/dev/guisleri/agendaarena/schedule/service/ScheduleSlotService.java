package br.dev.guisleri.agendaarena.schedule.service;

import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.service.EstablishmentService;
import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import br.dev.guisleri.agendaarena.schedule.exception.ScheduleSlotAlreadyExistsException;
import br.dev.guisleri.agendaarena.schedule.exception.ScheduleSlotNotAllowedException;
import br.dev.guisleri.agendaarena.schedule.exception.ScheduleSlotNotBookableException;
import br.dev.guisleri.agendaarena.schedule.exception.ScheduleSlotNotFoundException;
import br.dev.guisleri.agendaarena.schedule.repository.ScheduleSlotRepository;
import br.dev.guisleri.agendaarena.sportfield.entity.SportField;
import br.dev.guisleri.agendaarena.sportfield.service.SportFieldService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

@Service
public class ScheduleSlotService {

    private static final long SECONDS_PER_DAY = 24 * 60 * 60;

    private final SportFieldService sportFieldService;
    private final EstablishmentService establishmentService;
    private final ScheduleSlotRepository scheduleSlotRepository;

    public ScheduleSlotService(SportFieldService sportFieldService, EstablishmentService establishmentService, ScheduleSlotRepository scheduleSlotRepository) {
        this.sportFieldService = sportFieldService;
        this.establishmentService = establishmentService;
        this.scheduleSlotRepository = scheduleSlotRepository;
    }

    public ScheduleSlot createScheduleSlot(
            Long establishmentId,
            Long sportFieldId,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            BigDecimal price
    ) {
        SportField sportField = sportFieldService.findSportFieldById(
                establishmentId,
                sportFieldId
        );

        long startSeconds = startTime.toSecondOfDay();
        long durationSeconds = sportField.getSlotDurationMinutes() * 60L;
        long endSeconds = startSeconds + durationSeconds;

        if (endSeconds >= SECONDS_PER_DAY) {
            throw new ScheduleSlotNotAllowedException(
                    "Schedule slot cannot cross midnight"
            );
        }

        boolean scheduleSlotExists =
                scheduleSlotRepository.existsBySportFieldIdAndDayOfWeekAndStartTime(
                        sportFieldId,
                        dayOfWeek,
                        startTime
                );

        if (scheduleSlotExists) {
            throw new ScheduleSlotAlreadyExistsException(
                    "Schedule slot already exists for sport field "
                            + sportFieldId + " on " + dayOfWeek
                            + " at " + startTime
            );
        }

        LocalTime newStartTime = startTime;
        LocalTime newEndTime = startTime.plusMinutes(sportField.getSlotDurationMinutes());

        List<ScheduleSlot> existingSlots = scheduleSlotRepository.findAllBySportFieldIdAndDayOfWeek(
                sportFieldId,
                dayOfWeek
        );

        for (ScheduleSlot existingSlot : existingSlots) {

            LocalTime existingStartTime = existingSlot.getStartTime();
            LocalTime existingEndTime =
                    existingStartTime.plusMinutes(sportField.getSlotDurationMinutes());

            boolean overlaps =
                    newStartTime.isBefore(existingEndTime)
                            && existingStartTime.isBefore(newEndTime);

            if (overlaps) {
                throw new ScheduleSlotNotAllowedException(
                        "Schedule slot not allowed for sport field "
                                + sportFieldId + " on " + dayOfWeek
                                + " at " + startTime
                );
            }

        }

        ScheduleSlot scheduleSlot = new ScheduleSlot(
                sportField,
                dayOfWeek,
                startTime,
                price
        );

        return scheduleSlotRepository.save(scheduleSlot);
    }

    public List<ScheduleSlot> findAllScheduleSlots(
            Long establishmentId,
            Long sportFieldId
    ) {
        sportFieldService.findSportFieldById(
                establishmentId,
                sportFieldId
        );

        return scheduleSlotRepository.findAllBySportFieldId(sportFieldId);
    }

    public ScheduleSlot findScheduleSlotById(
            Long establishmentId,
            Long sportFieldId,
            Long scheduleSlotId
    ) {
        sportFieldService.findSportFieldById(
                establishmentId,
                sportFieldId
        );

        return scheduleSlotRepository
                .findByIdAndSportFieldId(
                        scheduleSlotId,
                        sportFieldId
                )
                .orElseThrow(() -> new ScheduleSlotNotFoundException(
                        "Schedule slot with id " + scheduleSlotId
                                + " not found for sport field " + sportFieldId
                ));


    }

    public ScheduleSlot findBookableScheduleSlot(
            Long establishmentId,
            Long sportFieldId,
            Long scheduleSlotId
    ) {

        ScheduleSlot scheduleSlot = findScheduleSlotById(
                establishmentId,
                sportFieldId,
                scheduleSlotId
        );

        Establishment establishment =
                establishmentService.findEstablishmentById(establishmentId);

        if (!establishment.isActive()) {
            throw new ScheduleSlotNotBookableException(
                    "Establishment with id " + establishmentId
                            + " is not active"
            );
        }

        if (!scheduleSlot.getSportField().isActive()) {
            throw new ScheduleSlotNotBookableException(
                    "Sport field with id " + sportFieldId
                            + " is not active"
            );
        }

        if (!scheduleSlot.isActive()) {
            throw new ScheduleSlotNotBookableException(
                    "Schedule slot with id " + scheduleSlotId
                            + " is not active"
            );
        }

        return scheduleSlot;

    }

}
