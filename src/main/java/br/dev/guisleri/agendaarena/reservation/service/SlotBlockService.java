package br.dev.guisleri.agendaarena.reservation.service;

import br.dev.guisleri.agendaarena.reservation.entity.SlotBlock;
import br.dev.guisleri.agendaarena.reservation.exception.SlotBlockConflictException;
import br.dev.guisleri.agendaarena.reservation.exception.SlotBlockNotAllowedException;
import br.dev.guisleri.agendaarena.reservation.exception.SlotBlockNotFoundException;
import br.dev.guisleri.agendaarena.reservation.repository.SlotBlockRepository;
import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import br.dev.guisleri.agendaarena.schedule.service.ScheduleSlotService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

@Service
public class SlotBlockService {

    private final ScheduleSlotService scheduleSlotService;
    private final SlotOccupancyService slotOccupancyService;
    private final SlotBlockRepository slotBlockRepository;
    private final Clock clock;

    public SlotBlockService(
            ScheduleSlotService scheduleSlotService,
            SlotOccupancyService slotOccupancyService,
            SlotBlockRepository slotBlockRepository,
            Clock clock
    ) {
        this.scheduleSlotService = scheduleSlotService;
        this.slotOccupancyService = slotOccupancyService;
        this.slotBlockRepository = slotBlockRepository;
        this.clock = clock;
    }

    /**
     * Bloqueia um slot em uma data. Segue o protocolo das demais operações que alteram a ocupação:
     * lock pessimista do slot primeiro, validações depois. Nunca sobrescreve uma reserva ou recorrência
     * existente: nesse caso o administrador precisa tratar o conflito explicitamente.
     */
    @Transactional
    public SlotBlock createBlock(
            Long establishmentId,
            Long sportFieldId,
            Long scheduleSlotId,
            LocalDate blockDate,
            String reason
    ) {
        ScheduleSlot scheduleSlot =
                scheduleSlotService.findScheduleSlotForUpdate(
                        establishmentId,
                        sportFieldId,
                        scheduleSlotId
                );

        validateBlockDate(scheduleSlot, blockDate);

        if (slotBlockRepository.existsByScheduleSlotIdAndBlockDate(
                scheduleSlotId,
                blockDate
        )) {
            throw new SlotBlockConflictException(
                    "Schedule slot is already blocked for this date"
            );
        }

        if (slotOccupancyService.isOccupied(scheduleSlotId, blockDate)) {
            throw new SlotBlockConflictException(
                    "Schedule slot is already occupied by a reservation on this date"
            );
        }

        return saveBlock(
                new SlotBlock(
                        scheduleSlot,
                        blockDate,
                        reason,
                        Instant.now(clock)
                )
        );
    }

    @Transactional
    public void deleteBlock(
            Long establishmentId,
            Long sportFieldId,
            Long scheduleSlotId,
            Long slotBlockId
    ) {
        scheduleSlotService.findScheduleSlotForUpdate(
                establishmentId,
                sportFieldId,
                scheduleSlotId
        );

        SlotBlock slotBlock =
                slotBlockRepository
                        .findByIdAndScheduleSlotId(slotBlockId, scheduleSlotId)
                        .orElseThrow(() ->
                                new SlotBlockNotFoundException(
                                        "Slot block with id " + slotBlockId
                                                + " not found for schedule slot "
                                                + scheduleSlotId
                                )
                        );

        slotBlockRepository.delete(slotBlock);
        slotBlockRepository.flush();
    }

    @Transactional(readOnly = true)
    public List<SlotBlock> findBlocks(
            Long establishmentId,
            Long sportFieldId,
            Long scheduleSlotId,
            LocalDate from
    ) {
        scheduleSlotService.findScheduleSlotById(
                establishmentId,
                sportFieldId,
                scheduleSlotId
        );

        LocalDate startDate = from != null
                ? from
                : ZonedDateTime.now(clock).toLocalDate();

        return slotBlockRepository
                .findAllByScheduleSlotIdAndBlockDateGreaterThanEqualOrderByBlockDate(
                        scheduleSlotId,
                        startDate
                );
    }

    private void validateBlockDate(ScheduleSlot scheduleSlot, LocalDate blockDate) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        LocalDate currentDate = now.toLocalDate();

        if (blockDate.isBefore(currentDate)) {
            throw new SlotBlockNotAllowedException(
                    "Block date cannot be in the past"
            );
        }

        if (!blockDate.getDayOfWeek().equals(scheduleSlot.getDayOfWeek())) {
            throw new SlotBlockNotAllowedException(
                    "Block date does not match schedule slot day of week"
            );
        }

        if (blockDate.equals(currentDate)
                && !scheduleSlot.getStartTime().isAfter(now.toLocalTime())) {
            throw new SlotBlockNotAllowedException(
                    "Block time cannot be in the past"
            );
        }
    }

    private SlotBlock saveBlock(SlotBlock slotBlock) {
        try {
            return slotBlockRepository.saveAndFlush(slotBlock);

        } catch (DataIntegrityViolationException exception) {

            String message = exception.getMostSpecificCause().getMessage();

            if (message != null && message.contains("uk_slot_blocks_slot_date")) {
                throw new SlotBlockConflictException(
                        "Schedule slot is already blocked for this date"
                );
            }

            throw exception;
        }
    }
}
