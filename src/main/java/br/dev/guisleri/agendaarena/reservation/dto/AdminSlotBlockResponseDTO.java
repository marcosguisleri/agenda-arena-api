package br.dev.guisleri.agendaarena.reservation.dto;

import br.dev.guisleri.agendaarena.reservation.entity.SlotBlock;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Linha da listagem de bloqueios do administrador. Traz o campo e o horário, com os ids que a
 * rota de desbloqueio ({@code DELETE .../sports-fields/{f}/schedule-slots/{s}/blocks/{id}}) pede.
 */
public record AdminSlotBlockResponseDTO(
        Long id,
        LocalDate blockDate,
        String reason,
        Instant createdAt,
        SportFieldSummary sportField,
        ScheduleSlotSummary scheduleSlot
) {

    public record SportFieldSummary(Long id, String name) {
    }

    public record ScheduleSlotSummary(
            Long id,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime
    ) {
    }

    public static AdminSlotBlockResponseDTO fromEntity(SlotBlock slotBlock) {
        var scheduleSlot = slotBlock.getScheduleSlot();
        var sportField = scheduleSlot.getSportField();

        return new AdminSlotBlockResponseDTO(
                slotBlock.getId(),
                slotBlock.getBlockDate(),
                slotBlock.getReason(),
                slotBlock.getCreatedAt(),
                new SportFieldSummary(sportField.getId(), sportField.getName()),
                new ScheduleSlotSummary(
                        scheduleSlot.getId(),
                        scheduleSlot.getDayOfWeek(),
                        scheduleSlot.getStartTime(),
                        scheduleSlot.getEndTime()
                )
        );
    }
}
