package br.dev.guisleri.agendaarena.establishment.controller;

import br.dev.guisleri.agendaarena.establishment.service.EstablishmentAdminService;
import br.dev.guisleri.agendaarena.user.dto.CreateUserRequestDTO;
import br.dev.guisleri.agendaarena.user.dto.UserResponseDTO;
import br.dev.guisleri.agendaarena.user.entity.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastro de administradores de um estabelecimento. Exclusivo do PLATFORM_ADMIN (ver
 * SecurityConfig).
 */
@RestController
@RequestMapping("/establishments/{establishmentId}/admins")
public class EstablishmentAdminController {

    private final EstablishmentAdminService establishmentAdminService;

    public EstablishmentAdminController(
            EstablishmentAdminService establishmentAdminService
    ) {
        this.establishmentAdminService = establishmentAdminService;
    }

    @PostMapping
    public ResponseEntity<UserResponseDTO> createAdmin(
            @PathVariable Long establishmentId,
            @Valid @RequestBody CreateUserRequestDTO requestDTO
    ) {
        User user = establishmentAdminService.createAdmin(
                establishmentId,
                requestDTO.name(),
                requestDTO.email(),
                requestDTO.phone(),
                requestDTO.password()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(UserResponseDTO.fromEntity(user));
    }

}
