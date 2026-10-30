package br.dev.guisleri.agendaarena.reservation.controller;

import br.dev.guisleri.agendaarena.reservation.dto.AgendaSlotResponseDTO;
import br.dev.guisleri.agendaarena.reservation.service.AgendaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/establishments/{establishmentId}/sports-fields/{sportFieldId}/agenda")
public class AgendaController {

    private final AgendaService agendaService;

    public AgendaController(AgendaService agendaService) {
        this.agendaService = agendaService;
    }

    @GetMapping
    public ResponseEntity<List<AgendaSlotResponseDTO>> findAgenda(
            @PathVariable Long establishmentId,
            @PathVariable Long sportFieldId,
            @RequestParam LocalDate date
    ) {
        return ResponseEntity.ok(
                agendaService.findAgenda(establishmentId, sportFieldId, date)
        );
    }
}
