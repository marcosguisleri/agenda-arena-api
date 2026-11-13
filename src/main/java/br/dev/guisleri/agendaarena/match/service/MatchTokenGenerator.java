package br.dev.guisleri.agendaarena.match.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Token do link público da partida: 32 bytes de {@link SecureRandom} (256 bits) em base64url sem
 * padding, sempre com 43 caracteres. Quem tem o link participa; sem ele, não dá para adivinhar.
 */
@Component
public class MatchTokenGenerator {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    public String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
