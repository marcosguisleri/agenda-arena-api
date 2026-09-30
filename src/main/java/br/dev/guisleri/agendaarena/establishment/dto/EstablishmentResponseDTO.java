package br.dev.guisleri.agendaarena.establishment.dto;

import br.dev.guisleri.agendaarena.establishment.entity.Establishment;

public record EstablishmentResponseDTO(
        Long id,
        String name,
        boolean active
) {

    public static EstablishmentResponseDTO fromEntity(
            Establishment establishment
    ) {
        return new EstablishmentResponseDTO(
                establishment.getId(),
                establishment.getName(),
                establishment.isActive()
        );
    }
}
