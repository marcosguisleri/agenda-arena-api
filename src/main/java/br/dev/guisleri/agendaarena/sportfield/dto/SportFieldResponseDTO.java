package br.dev.guisleri.agendaarena.sportfield.dto;

import br.dev.guisleri.agendaarena.sportfield.entity.SportField;
import br.dev.guisleri.agendaarena.sportfield.model.SportType;

public record SportFieldResponseDTO(
        Long id,
        Long establishmentId,
        String name,
        SportType sportType,
        boolean active
) {
    public static SportFieldResponseDTO fromEntity(SportField sportField) {
        return new SportFieldResponseDTO(
                sportField.getId(),
                sportField.getEstablishment().getId(),
                sportField.getName(),
                sportField.getSportType(),
                sportField.isActive()
        );
    }
}
