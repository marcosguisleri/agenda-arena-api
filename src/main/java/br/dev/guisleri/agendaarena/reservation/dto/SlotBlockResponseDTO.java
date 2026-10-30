package br.dev.guisleri.agendaarena.reservation.dto;

import br.dev.guisleri.agendaarena.reservation.entity.SlotBlock;

import java.time.Instant;
import java.time.LocalDate;

public record SlotBlockResponseDTO(
        Long id,
        Long scheduleSlotId,
        LocalDate blockDate,
        String reason,
        Instant createdAt
) {
    public static SlotBlockResponseDTO fromEntity(SlotBlock slotBlock) {
        return new SlotBlockResponseDTO(
                slotBlock.getId(),
                slotBlock.getScheduleSlot().getId(),
                slotBlock.getBlockDate(),
                slotBlock.getReason(),
                slotBlock.getCreatedAt()
        );
    }
}
