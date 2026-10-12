package br.dev.guisleri.agendaarena.schedule.controller;

import br.dev.guisleri.agendaarena.schedule.dto.CreateScheduleSlotRequestDTO;
import br.dev.guisleri.agendaarena.schedule.dto.ScheduleSlotResponseDTO;
import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import br.dev.guisleri.agendaarena.schedule.service.ScheduleSlotService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(
        "/establishments/{establishmentId}/sports-fields/{sportFieldId}/schedule-slots"
)
public class ScheduleSlotController {

    private final ScheduleSlotService scheduleSlotService;

    public ScheduleSlotController(ScheduleSlotService scheduleSlotService) {
        this.scheduleSlotService = scheduleSlotService;
    }

    @PostMapping
    public ResponseEntity<ScheduleSlotResponseDTO> createScheduleSlot(
            @PathVariable Long establishmentId,
            @PathVariable Long sportFieldId,
            @Valid @RequestBody CreateScheduleSlotRequestDTO requestDTO
    ) {
        ScheduleSlot scheduleSlot = scheduleSlotService.createScheduleSlot(
                establishmentId,
                sportFieldId,
                requestDTO.dayOfWeek(),
                requestDTO.startTime(),
                requestDTO.price()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ScheduleSlotResponseDTO.fromEntity(scheduleSlot));
    }

    @GetMapping
    public ResponseEntity<List<ScheduleSlotResponseDTO>> getAllScheduleSlots(
            @PathVariable Long establishmentId,
            @PathVariable Long sportFieldId
    ) {
        List<ScheduleSlot> scheduleSlots = scheduleSlotService.findAllScheduleSlots(
                establishmentId,
                sportFieldId
        );

        return ResponseEntity.ok(
                scheduleSlots.stream()
                        .map(ScheduleSlotResponseDTO::fromEntity)
                        .toList()
        );
    }

    @GetMapping("/{scheduleSlotId}")
    public ResponseEntity<ScheduleSlotResponseDTO> getScheduleSlotById(
            @PathVariable Long establishmentId,
            @PathVariable Long sportFieldId,
            @PathVariable Long scheduleSlotId
    ) {
        ScheduleSlot scheduleSlot = scheduleSlotService.findScheduleSlotById(
                establishmentId,
                sportFieldId,
                scheduleSlotId
        );

        return ResponseEntity.ok(
                ScheduleSlotResponseDTO.fromEntity(scheduleSlot)
        );
    }

}
