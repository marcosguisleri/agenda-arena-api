package br.dev.guisleri.agendaarena.auth.entity;

import br.dev.guisleri.agendaarena.user.entity.User;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Uma sessão de login renovável. Guarda só o hash do refresh token atual e do anterior; a
 * renovação troca o atual (o antigo vira "anterior"). Quem apresentar o token anterior de novo
 * está reutilizando um token já trocado, e a sessão inteira deve ser revogada.
 */
@Entity
@Table(name = "refresh_sessions")
public class RefreshSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "current_token_hash", nullable = false, length = 64)
    private String currentTokenHash;

    @Column(name = "previous_token_hash", length = 64)
    private String previousTokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_rotated_at", nullable = false)
    private Instant lastRotatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected RefreshSession() {
    }

    public static RefreshSession start(
            User user,
            String tokenHash,
            Instant now,
            Instant expiresAt
    ) {
        RefreshSession session = new RefreshSession();
        session.user = user;
        session.currentTokenHash = tokenHash;
        session.createdAt = now;
        session.lastRotatedAt = now;
        session.expiresAt = expiresAt;
        return session;
    }

    public void rotate(String newTokenHash, Instant now, Instant newExpiresAt) {
        this.previousTokenHash = this.currentTokenHash;
        this.currentTokenHash = newTokenHash;
        this.lastRotatedAt = now;
        this.expiresAt = newExpiresAt;
    }

    public void revoke(Instant now) {
        if (this.revokedAt == null) {
            this.revokedAt = now;
        }
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    /**
     * O token apresentado é o anterior ao atual, ou seja, já foi trocado por uma renovação.
     */
    public boolean wasAlreadyRotated(String tokenHash) {
        return previousTokenHash != null
                && previousTokenHash.equals(tokenHash)
                && !currentTokenHash.equals(tokenHash);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
