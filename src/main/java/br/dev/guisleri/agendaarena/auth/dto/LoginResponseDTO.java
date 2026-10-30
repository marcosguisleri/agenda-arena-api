package br.dev.guisleri.agendaarena.auth.dto;

public record LoginResponseDTO(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
