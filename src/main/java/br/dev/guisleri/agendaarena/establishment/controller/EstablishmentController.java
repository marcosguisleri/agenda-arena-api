package br.dev.guisleri.agendaarena.establishment.controller;

import br.dev.guisleri.agendaarena.establishment.dto.CreateEstablishmentRequestDTO;
import br.dev.guisleri.agendaarena.establishment.dto.EstablishmentResponseDTO;
import br.dev.guisleri.agendaarena.establishment.dto.UpdateEstablishmentStatusRequestDTO;
import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.service.EstablishmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/establishments")
public class EstablishmentController {

    private final EstablishmentService establishmentService;

    public EstablishmentController(
            EstablishmentService establishmentService
    ) {
        this.establishmentService = establishmentService;
    }

    @PostMapping
    public ResponseEntity<EstablishmentResponseDTO> createEstablishment(
            @Valid @RequestBody CreateEstablishmentRequestDTO requestDTO
    ) {

        Establishment establishment =
                establishmentService.createEstablishment(
                        requestDTO.name()
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        EstablishmentResponseDTO.fromEntity(establishment)
                );
    }

    @PatchMapping("/{establishmentId}/status")
    public ResponseEntity<Void> updateEstablishmentStatus(
            @PathVariable Long establishmentId,
            @Valid @RequestBody UpdateEstablishmentStatusRequestDTO requestDTO
    ) {
        establishmentService.updateEstablishmentStatus(establishmentId, requestDTO.active());

        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<EstablishmentResponseDTO>> findAllEstablishments() {
        List<Establishment> establishments = establishmentService.findAllEstablishments();

        return ResponseEntity.ok(
                establishments.stream()
                        .map(EstablishmentResponseDTO::fromEntity)
                        .toList()
        );
    }

    @GetMapping("/{establishmentId}")
    public ResponseEntity<EstablishmentResponseDTO> findEstablishmentById(
            @PathVariable Long establishmentId
    ) {
        Establishment establishment = establishmentService.findEstablishmentById(establishmentId);
        return ResponseEntity.ok(EstablishmentResponseDTO.fromEntity(establishment));
    }
}
