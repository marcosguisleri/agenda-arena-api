package br.dev.guisleri.agendaarena.reservation.controller;

import br.dev.guisleri.agendaarena.common.dto.PageRequests;
import br.dev.guisleri.agendaarena.common.dto.PageResponseDTO;
import br.dev.guisleri.agendaarena.reservation.dto.AdminRecurringReservationResponseDTO;
import br.dev.guisleri.agendaarena.reservation.model.RecurringReservationListStatus;
import br.dev.guisleri.agendaarena.reservation.service.AdminRecurringReservationListService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/establishments/{establishmentId}/recurring-reservations")
public class AdminRecurringReservationController {

    private final AdminRecurringReservationListService adminRecurringReservationListService;

    public AdminRecurringReservationController(
            AdminRecurringReservationListService adminRecurringReservationListService
    ) {
        this.adminRecurringReservationListService = adminRecurringReservationListService;
    }

    @GetMapping
    public ResponseEntity<PageResponseDTO<AdminRecurringReservationResponseDTO>>
    listRecurringReservations(
            @PathVariable Long establishmentId,
            @RequestParam(required = false) Long sportFieldId,
            @RequestParam(required = false) List<RecurringReservationListStatus> status,
            @RequestParam(required = false) Boolean hasOrganizer,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_SIZE) int size
    ) {
        return ResponseEntity.ok(
                adminRecurringReservationListService.list(
                        establishmentId,
                        sportFieldId,
                        status,
                        hasOrganizer,
                        page,
                        size
                )
        );
    }
}
