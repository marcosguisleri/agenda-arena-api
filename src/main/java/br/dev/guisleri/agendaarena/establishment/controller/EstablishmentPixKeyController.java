package br.dev.guisleri.agendaarena.establishment.controller;

import br.dev.guisleri.agendaarena.establishment.service.EstablishmentService;
import br.dev.guisleri.agendaarena.pix.dto.PixKeyRequestDTO;
import br.dev.guisleri.agendaarena.pix.dto.PixKeyResponseDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Chave Pix do estabelecimento (gestão: só o admin do estabelecimento ou a plataforma). Nunca
 * aparece nas rotas públicas do catálogo; o organizador a escolhe ao criar a partida.
 */
@RestController
@RequestMapping("/establishments/{establishmentId}/pix-key")
public class EstablishmentPixKeyController {

    private final EstablishmentService establishmentService;

    public EstablishmentPixKeyController(EstablishmentService establishmentService) {
        this.establishmentService = establishmentService;
    }

    @PutMapping
    public ResponseEntity<PixKeyResponseDTO> changePixKey(
            @PathVariable Long establishmentId,
            @Valid @RequestBody PixKeyRequestDTO requestDTO
    ) {
        return ResponseEntity.ok(PixKeyResponseDTO.fromEntity(
                establishmentService.changePixKey(
                        establishmentId,
                        requestDTO.keyType(),
                        requestDTO.key(),
                        requestDTO.holderName(),
                        requestDTO.bankName()
                )
        ));
    }

    @GetMapping
    public ResponseEntity<PixKeyResponseDTO> findPixKey(@PathVariable Long establishmentId) {
        return ResponseEntity.ok(PixKeyResponseDTO.fromEntity(
                establishmentService.findPixKey(establishmentId)
        ));
    }

    @DeleteMapping
    public ResponseEntity<Void> removePixKey(@PathVariable Long establishmentId) {
        establishmentService.removePixKey(establishmentId);

        return ResponseEntity.noContent().build();
    }
}
