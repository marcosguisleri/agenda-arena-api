package br.dev.guisleri.agendaarena.establishment.controller;

import br.dev.guisleri.agendaarena.common.security.AuthenticatedUsers;
import br.dev.guisleri.agendaarena.establishment.dto.EstablishmentResponseDTO;
import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.service.EstablishmentAdminService;
import br.dev.guisleri.agendaarena.establishment.service.EstablishmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Estabelecimentos que o usuário logado administra (base do painel do administrador).
 */
@RestController
@RequestMapping("/me/establishments")
public class MeEstablishmentController {

    private final EstablishmentAdminService establishmentAdminService;
    private final EstablishmentService establishmentService;

    public MeEstablishmentController(
            EstablishmentAdminService establishmentAdminService,
            EstablishmentService establishmentService
    ) {
        this.establishmentAdminService = establishmentAdminService;
        this.establishmentService = establishmentService;
    }

    @GetMapping
    public ResponseEntity<List<EstablishmentResponseDTO>> findMyEstablishments(
            Authentication authentication
    ) {
        List<Establishment> establishments =
                AuthenticatedUsers.hasRole(authentication, AuthenticatedUsers.PLATFORM_ADMIN)
                        ? establishmentService.findAllEstablishments()
                        : establishmentAdminService.findManagedEstablishments(
                        AuthenticatedUsers.userId(authentication)
                );

        return ResponseEntity.ok(
                establishments.stream()
                        .map(EstablishmentResponseDTO::fromEntity)
                        .toList()
        );
    }

}
