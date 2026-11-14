package br.dev.guisleri.agendaarena.reservation.controller;

import br.dev.guisleri.agendaarena.common.dto.PageRequests;
import br.dev.guisleri.agendaarena.common.dto.PageResponseDTO;
import br.dev.guisleri.agendaarena.reservation.dto.AdminReservationResponseDTO;
import br.dev.guisleri.agendaarena.reservation.model.ReservationOrigin;
import br.dev.guisleri.agendaarena.reservation.model.ReservationStatus;
import br.dev.guisleri.agendaarena.reservation.service.AdminReservationListService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/establishments/{establishmentId}/reservations")
public class AdminReservationController {

    private final AdminReservationListService adminReservationListService;

    public AdminReservationController(AdminReservationListService adminReservationListService) {
        this.adminReservationListService = adminReservationListService;
    }

    @GetMapping
    public ResponseEntity<PageResponseDTO<AdminReservationResponseDTO>> listReservations(
            @PathVariable Long establishmentId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Long sportFieldId,
            @RequestParam(required = false) List<ReservationStatus> status,
            @RequestParam(required = false) ReservationOrigin origin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_SIZE) int size
    ) {
        return ResponseEntity.ok(
                adminReservationListService.list(
                        establishmentId,
                        from,
                        to,
                        sportFieldId,
                        status,
                        origin,
                        page,
                        size
                )
        );
    }
}
