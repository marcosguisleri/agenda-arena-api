package br.dev.guisleri.agendaarena.match.entity;

import br.dev.guisleri.agendaarena.match.model.MatchTeam;
import br.dev.guisleri.agendaarena.match.model.PlayerPosition;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
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

    /**
     * Entra no rateio. Padrão: linha paga, goleiro é isento.
     */
    @Column(name = "pays_share", nullable = false)
    private boolean paysShare;

    /**
     * Valor congelado ao fechar a partida; nulo enquanto ela está aberta.
     */
    @Column(name = "share_amount", precision = 10, scale = 2)
    private BigDecimal shareAmount;

    /**
     * Pagamento declarado manualmente (não é confirmação bancária).
     */
    @Column(nullable = false)
    private boolean paid;

    /**
     * Valor da cota quando o pagamento foi declarado.
     */
    @Column(name = "paid_amount", precision = 10, scale = 2)
    private BigDecimal paidAmount;

    /**
     * Time A ou B (sorteio ou montagem manual); nulo = sem time. Com tamanho 1 o Hibernate
     * esperaria CHAR(1); a coluna é VARCHAR(1) (V17).
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 1)
    private MatchTeam team;

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
        participant.paysShare = paysShareByDefault(position);
        participant.paid = false;

        return participant;
    }

    public static boolean paysShareByDefault(PlayerPosition position) {
        return position != PlayerPosition.GOALKEEPER;
    }

    /**
     * Troca a posição e volta à regra padrão de rateio da nova posição.
     */
    public void changePosition(PlayerPosition position) {
        this.position = position;
        this.paysShare = paysShareByDefault(position);
    }

    public void changePaysShare(boolean paysShare) {
        this.paysShare = paysShare;
    }

    public void freezeShare(BigDecimal shareAmount) {
        this.shareAmount = shareAmount;
    }

    public void unfreezeShare() {
        this.shareAmount = null;
    }

    public void markPaid(BigDecimal paidAmount) {
        this.paid = true;
        this.paidAmount = paidAmount;
    }

    public void unmarkPaid() {
        this.paid = false;
        this.paidAmount = null;
    }

    /**
     * @param team A, B ou {@code null} (sem time)
     */
    public void assignTeam(MatchTeam team) {
        this.team = team;
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

    public boolean isPaysShare() {
        return paysShare;
    }

    public BigDecimal getShareAmount() {
        return shareAmount;
    }

    public boolean isPaid() {
        return paid;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public MatchTeam getTeam() {
        return team;
    }
}
