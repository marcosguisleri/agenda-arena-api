package br.dev.guisleri.agendaarena.availability.controller;

import br.dev.guisleri.agendaarena.availability.dto.AvailabilityResponseDTO;
import br.dev.guisleri.agendaarena.availability.service.AvailabilityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping(
        "/establishments/{establishmentId}/sports-fields/{sportFieldId}/availability"
)
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping
    public ResponseEntity<List<AvailabilityResponseDTO>> findAvailability(
            @PathVariable Long establishmentId,
            @PathVariable Long sportFieldId,
            @RequestParam LocalDate date
    ) {
        List<AvailabilityResponseDTO> availability =
                availabilityService.findAvailability(
                        establishmentId,
                        sportFieldId,
                        date
                );

        return ResponseEntity.ok(availability);
    }
}
