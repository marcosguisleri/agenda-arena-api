package br.dev.guisleri.agendaarena.auth.service;

/**
 * Par entregue no login e na renovação: access token (JWT) e refresh token (opaco).
 */
public record AuthTokens(
        String accessToken,
        long expiresInSeconds,
        String refreshToken,
        long refreshExpiresInSeconds
) {
}
