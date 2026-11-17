package br.dev.guisleri.agendaarena.reservation.controller;

import br.dev.guisleri.agendaarena.common.dto.PageRequests;
import br.dev.guisleri.agendaarena.common.dto.PageResponseDTO;
import br.dev.guisleri.agendaarena.reservation.dto.AdminSlotBlockResponseDTO;
import br.dev.guisleri.agendaarena.reservation.service.AdminSlotBlockListService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/establishments/{establishmentId}/blocks")
public class AdminSlotBlockController {

    private final AdminSlotBlockListService adminSlotBlockListService;

    public AdminSlotBlockController(AdminSlotBlockListService adminSlotBlockListService) {
        this.adminSlotBlockListService = adminSlotBlockListService;
    }

    @GetMapping
    public ResponseEntity<PageResponseDTO<AdminSlotBlockResponseDTO>> listBlocks(
            @PathVariable Long establishmentId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long sportFieldId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_SIZE) int size
    ) {
        return ResponseEntity.ok(
                adminSlotBlockListService.list(
                        establishmentId,
                        from,
                        to,
                        sportFieldId,
                        page,
                        size
                )
        );
    }
}
