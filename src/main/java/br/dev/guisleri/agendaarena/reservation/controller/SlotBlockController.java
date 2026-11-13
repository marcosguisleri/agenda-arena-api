package br.dev.guisleri.agendaarena.reservation.controller;

import br.dev.guisleri.agendaarena.reservation.dto.CreateSlotBlockRequestDTO;
import br.dev.guisleri.agendaarena.reservation.dto.SlotBlockResponseDTO;
import br.dev.guisleri.agendaarena.reservation.entity.SlotBlock;
import br.dev.guisleri.agendaarena.reservation.service.SlotBlockService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping(
        "/establishments/{establishmentId}/sports-fields/{sportFieldId}/schedule-slots/{scheduleSlotId}/blocks"
)
public class SlotBlockController {

    private final SlotBlockService slotBlockService;

    public SlotBlockController(SlotBlockService slotBlockService) {
        this.slotBlockService = slotBlockService;
    }

    @PostMapping
    public ResponseEntity<SlotBlockResponseDTO> createBlock(
            @PathVariable Long establishmentId,
            @PathVariable Long sportFieldId,
            @PathVariable Long scheduleSlotId,
            @Valid @RequestBody CreateSlotBlockRequestDTO requestDTO
    ) {
        SlotBlock slotBlock =
                slotBlockService.createBlock(
                        establishmentId,
                        sportFieldId,
                        scheduleSlotId,
                        requestDTO.blockDate(),
                        requestDTO.reason()
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SlotBlockResponseDTO.fromEntity(slotBlock));
    }

    @GetMapping
    public ResponseEntity<List<SlotBlockResponseDTO>> findBlocks(
            @PathVariable Long establishmentId,
            @PathVariable Long sportFieldId,
            @PathVariable Long scheduleSlotId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from
    ) {
        List<SlotBlockResponseDTO> blocks =
                slotBlockService
                        .findBlocks(
                                establishmentId,
                                sportFieldId,
                                scheduleSlotId,
                                from
                        )
                        .stream()
                        .map(SlotBlockResponseDTO::fromEntity)
                        .toList();

        return ResponseEntity.ok(blocks);
    }

    @DeleteMapping("/{slotBlockId}")
    public ResponseEntity<Void> deleteBlock(
            @PathVariable Long establishmentId,
            @PathVariable Long sportFieldId,
            @PathVariable Long scheduleSlotId,
            @PathVariable Long slotBlockId
    ) {
        slotBlockService.deleteBlock(
                establishmentId,
                sportFieldId,
                scheduleSlotId,
                slotBlockId
        );

        return ResponseEntity.noContent().build();
    }
}
