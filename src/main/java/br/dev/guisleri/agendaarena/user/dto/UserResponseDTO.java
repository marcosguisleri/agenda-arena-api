package br.dev.guisleri.agendaarena.user.dto;

import br.dev.guisleri.agendaarena.user.entity.User;

public record UserResponseDTO(
        Long id,
        String name,
        String email,
        String phone,
        boolean active
) {
    public static UserResponseDTO fromEntity(User user) {
        return new UserResponseDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.isActive()
        );
    }
}
