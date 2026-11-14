package br.dev.guisleri.agendaarena.auth.dto;

import br.dev.guisleri.agendaarena.auth.service.AuthTokens;

/**
 * Resposta do login e da renovação: o mesmo formato nos dois.
 */
public record LoginResponseDTO(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        long refreshExpiresIn
) {

    public static LoginResponseDTO fromTokens(AuthTokens tokens) {
        return new LoginResponseDTO(
                tokens.accessToken(),
                "Bearer",
                tokens.expiresInSeconds(),
                tokens.refreshToken(),
                tokens.refreshExpiresInSeconds()
        );
    }
}
