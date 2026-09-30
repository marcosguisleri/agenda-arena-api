package br.dev.guisleri.agendaarena.establishment.controller;

import br.dev.guisleri.agendaarena.establishment.dto.CreateEstablishmentRequestDTO;
import br.dev.guisleri.agendaarena.establishment.dto.EstablishmentResponseDTO;
import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.service.EstablishmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping
    public List<EstablishmentResponseDTO> findAllEstablishments() {
        return establishmentService.findAllEstablishments()
                .stream()
                .map(EstablishmentResponseDTO::fromEntity)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<EstablishmentResponseDTO> findEstablishmentById(
            @PathVariable Long id
    ) {
        Establishment establishment = establishmentService.findEstablishmentById(id);
        return ResponseEntity.ok(EstablishmentResponseDTO.fromEntity(establishment));
    }
}
