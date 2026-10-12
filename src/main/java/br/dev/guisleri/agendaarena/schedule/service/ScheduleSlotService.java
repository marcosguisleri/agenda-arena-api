package br.dev.guisleri.agendaarena.schedule.service;

import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import br.dev.guisleri.agendaarena.schedule.exception.ScheduleSlotAlreadyExistsException;
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

    private final SportFieldService sportFieldService;
    private final ScheduleSlotRepository scheduleSlotRepository;

    public ScheduleSlotService(SportFieldService sportFieldService, ScheduleSlotRepository scheduleSlotRepository) {
        this.sportFieldService = sportFieldService;
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
}
