package br.dev.guisleri.agendaarena.auth.service;

import br.dev.guisleri.agendaarena.auth.entity.RefreshSession;
import br.dev.guisleri.agendaarena.auth.exception.InvalidRefreshTokenException;
import br.dev.guisleri.agendaarena.auth.repository.RefreshSessionRepository;
import br.dev.guisleri.agendaarena.common.config.AppSecurityProperties;
import br.dev.guisleri.agendaarena.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Sessões renováveis. A renovação troca o refresh token a cada uso (rotação); reaproveitar um
 * token já trocado revoga a sessão inteira. Tudo que falha devolve a mesma mensagem genérica,
 * sem dizer se o token não existe, expirou ou foi revogado.
 */
@Service
public class RefreshTokenService {

    static final String INVALID_REFRESH_TOKEN_MESSAGE = "Invalid refresh token";
    private static final long DEFAULT_EXPIRATION_DAYS = 30;

    private final RefreshSessionRepository refreshSessionRepository;
    private final RefreshTokenGenerator tokenGenerator;
    private final JwtTokenService jwtTokenService;
    private final Clock clock;
    private final Duration expiration;

    public RefreshTokenService(
            RefreshSessionRepository refreshSessionRepository,
            RefreshTokenGenerator tokenGenerator,
            JwtTokenService jwtTokenService,
            Clock clock,
            AppSecurityProperties properties
    ) {
        this.refreshSessionRepository = refreshSessionRepository;
        this.tokenGenerator = tokenGenerator;
        this.jwtTokenService = jwtTokenService;
        this.clock = clock;

        long days = properties.refresh() == null || properties.refresh().expirationDays() <= 0
                ? DEFAULT_EXPIRATION_DAYS
                : properties.refresh().expirationDays();

        this.expiration = Duration.ofDays(days);
    }

    /**
     * Abre uma sessão no login e devolve o refresh token (a única vez em que ele aparece em
     * claro). Aproveita para apagar as sessões já expiradas ou revogadas do mesmo usuário.
     */
    @Transactional
    public IssuedRefreshToken start(User user) {
        Instant now = clock.instant();

        refreshSessionRepository.deleteFinishedByUserId(user.getId(), now);

        String token = tokenGenerator.newToken();

        refreshSessionRepository.save(
                RefreshSession.start(
                        user,
                        tokenGenerator.hash(token),
                        now,
                        now.plus(expiration)
                )
        );

        return new IssuedRefreshToken(token, expiration.toSeconds());
    }

    /**
     * Troca o refresh token por um par novo. Trava a linha da sessão antes de validar. A revogação
     * por reuso ou por usuário inativo precisa ser gravada mesmo com a exceção: por isso
     * {@code noRollbackFor}.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthTokens rotate(String refreshToken) {
        String tokenHash = tokenGenerator.hash(refreshToken);
        Instant now = clock.instant();

        RefreshSession session = refreshSessionRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow(RefreshTokenService::invalidRefreshToken);

        if (session.isRevoked()) {
            throw invalidRefreshToken();
        }

        if (session.wasAlreadyRotated(tokenHash)) {
            session.revoke(now);
            refreshSessionRepository.saveAndFlush(session);
            throw invalidRefreshToken();
        }

        if (session.isExpired(now)) {
            throw invalidRefreshToken();
        }

        User user = session.getUser();

        if (!user.isActive()) {
            session.revoke(now);
            refreshSessionRepository.saveAndFlush(session);
            throw invalidRefreshToken();
        }

        String newToken = tokenGenerator.newToken();

        session.rotate(tokenGenerator.hash(newToken), now, now.plus(expiration));
        refreshSessionRepository.saveAndFlush(session);

        JwtTokenService.IssuedToken accessToken = jwtTokenService.issue(user);

        return new AuthTokens(
                accessToken.accessToken(),
                accessToken.expiresInSeconds(),
                newToken,
                expiration.toSeconds()
        );
    }

    /**
     * Encerra a sessão do token (atual ou anterior). Idempotente: token desconhecido ou sessão já
     * encerrada não é erro.
     */
    @Transactional
    public void revoke(String refreshToken) {
        String tokenHash = tokenGenerator.hash(refreshToken);

        refreshSessionRepository
                .findByTokenHashForUpdate(tokenHash)
                .ifPresent(session -> {
                    session.revoke(clock.instant());
                    refreshSessionRepository.saveAndFlush(session);
                });
    }

    private static InvalidRefreshTokenException invalidRefreshToken() {
        return new InvalidRefreshTokenException(INVALID_REFRESH_TOKEN_MESSAGE);
    }

    public record IssuedRefreshToken(String token, long expiresInSeconds) {
    }
}
