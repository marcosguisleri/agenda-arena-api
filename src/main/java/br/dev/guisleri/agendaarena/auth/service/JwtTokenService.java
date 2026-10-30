package br.dev.guisleri.agendaarena.auth.service;

import br.dev.guisleri.agendaarena.common.config.AppSecurityProperties;
import br.dev.guisleri.agendaarena.user.entity.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Emite o access token (JWT HS256). O token leva só o id do usuário ({@code sub}) e o papel
 * ({@code roles}); o vínculo com estabelecimentos é consultado no banco a cada requisição.
 */
@Service
public class JwtTokenService {

    private static final String ISSUER = "agenda-arena-api";
    private static final long DEFAULT_EXPIRATION_MINUTES = 60;

    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final Duration expiration;

    public JwtTokenService(
            JwtEncoder jwtEncoder,
            Clock clock,
            AppSecurityProperties properties
    ) {
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;

        long minutes = properties.jwt() == null || properties.jwt().expirationMinutes() <= 0
                ? DEFAULT_EXPIRATION_MINUTES
                : properties.jwt().expirationMinutes();

        this.expiration = Duration.ofMinutes(minutes);
    }

    public IssuedToken issue(User user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(expiration);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", List.of(user.getRole().name()))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();

        return new IssuedToken(token, expiration.toSeconds());
    }

    public record IssuedToken(String accessToken, long expiresInSeconds) {
    }
}
