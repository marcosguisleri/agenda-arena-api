package br.dev.guisleri.agendaarena.match.entity;

import br.dev.guisleri.agendaarena.match.model.MatchStatus;
import br.dev.guisleri.agendaarena.reservation.entity.Reservation;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Página da partida de UMA reserva. Não agrupa reservas consecutivas.
 */
@Entity
@Table(name = "matches")
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true)
    private Reservation reservation;

    @Column(name = "public_token", nullable = false, unique = true, length = 43)
    private String publicToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MatchStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Match() {
    }

    public static Match open(Reservation reservation, String publicToken, Instant createdAt) {
        Match match = new Match();

        match.reservation = reservation;
        match.publicToken = publicToken;
        match.status = MatchStatus.OPEN;
        match.createdAt = createdAt;

        return match;
    }

    public Long getId() {
        return id;
    }

    public Reservation getReservation() {
        return reservation;
    }

    public String getPublicToken() {
        return publicToken;
    }

    public MatchStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
