package br.dev.guisleri.agendaarena.match.entity;

import br.dev.guisleri.agendaarena.match.model.PlayerPosition;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Jogador inscrito na partida. Não tem conta: é identificado pelo id, não pelo nome.
 */
@Entity
@Table(name = "match_participants")
public class MatchParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Column(nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlayerPosition position;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MatchParticipant() {
    }

    public static MatchParticipant join(
            Match match,
            String name,
            PlayerPosition position,
            Instant createdAt
    ) {
        MatchParticipant participant = new MatchParticipant();

        participant.match = match;
        participant.name = name;
        participant.position = position;
        participant.createdAt = createdAt;

        return participant;
    }

    public void changePosition(PlayerPosition position) {
        this.position = position;
    }

    public Long getId() {
        return id;
    }

    public Match getMatch() {
        return match;
    }

    public String getName() {
        return name;
    }

    public PlayerPosition getPosition() {
        return position;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
