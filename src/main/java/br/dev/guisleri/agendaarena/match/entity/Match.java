package br.dev.guisleri.agendaarena.match.entity;

import br.dev.guisleri.agendaarena.match.model.MatchStatus;
import br.dev.guisleri.agendaarena.match.model.MatchTeam;
import br.dev.guisleri.agendaarena.match.model.ShirtColor;
import br.dev.guisleri.agendaarena.reservation.entity.RecurringReservation;
import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import br.dev.guisleri.agendaarena.user.entity.User;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Página da partida de UM horário: uma reserva avulsa ou uma ocorrência de recorrência (nunca as
 * duas; {@code chk_matches_single_source}). Não agrupa reservas consecutivas.
 * <p>
 * Dono, horário, data e preço ficam copiados na partida na criação: as regras e as telas leem
 * daqui, sem depender da origem.
 */
@Entity
@Table(name = "matches")
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Origem reserva avulsa; nula quando a origem é uma ocorrência de recorrência.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", unique = true)
    private Reservation reservation;

    /**
     * Origem ocorrência de recorrência (série + data); nulas quando a origem é uma reserva.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recurring_reservation_id")
    private RecurringReservation recurringReservation;

    @Column(name = "occurrence_date")
    private LocalDate occurrenceDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_id", nullable = false)
    private User organizer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_slot_id", nullable = false)
    private ScheduleSlot scheduleSlot;

    @Column(name = "match_date", nullable = false)
    private LocalDate date;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /**
     * Valor total rateado, copiado na criação (nunca alterado retroativamente).
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "public_token", nullable = false, unique = true, length = 43)
    private String publicToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MatchStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Chave Pix só exibida na página; o pagamento acontece fora da plataforma.
     */
    @Column(name = "pix_key", length = 140)
    private String pixKey;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "team_a_name", nullable = false, length = 30)
    private String teamAName;

    @Enumerated(EnumType.STRING)
    @Column(name = "team_a_shirt_color", length = 20)
    private ShirtColor teamAShirtColor;

    @Column(name = "team_b_name", nullable = false, length = 30)
    private String teamBName;

    @Enumerated(EnumType.STRING)
    @Column(name = "team_b_shirt_color", length = 20)
    private ShirtColor teamBShirtColor;

    /**
     * Última alteração dos times (sorteio ou movimento manual); nulo se nunca foram montados.
     */
    @Column(name = "teams_updated_at")
    private Instant teamsUpdatedAt;

    protected Match() {
    }

    /**
     * Partida de uma reserva avulsa: copia dono, horário, data e preço da reserva.
     */
    public static Match forReservation(
            Reservation reservation,
            String publicToken,
            Instant createdAt
    ) {
        Match match = open(publicToken, createdAt);

        match.reservation = reservation;
        match.organizer = reservation.getOrganizer();
        match.scheduleSlot = reservation.getScheduleSlot();
        match.date = reservation.getReservationDate();
        match.startTime = reservation.getStartTime();
        match.endTime = reservation.getEndTime();
        match.price = reservation.getPrice();

        return match;
    }

    private static Match open(String publicToken, Instant createdAt) {
        Match match = new Match();

        match.publicToken = publicToken;
        match.status = MatchStatus.OPEN;
        match.createdAt = createdAt;
        match.teamAName = "Time A";
        match.teamBName = "Time B";

        return match;
    }

    public void close(Instant closedAt) {
        this.status = MatchStatus.CLOSED;
        this.closedAt = closedAt;
    }

    /**
     * Reabrir mantém os times: quem se inscrever depois fica sem time.
     */
    public void reopen() {
        this.status = MatchStatus.OPEN;
        this.closedAt = null;
    }

    public void markTeamsUpdated(Instant teamsUpdatedAt) {
        this.teamsUpdatedAt = teamsUpdatedAt;
    }

    public void changeTeam(MatchTeam team, String name, ShirtColor shirtColor) {
        if (team == MatchTeam.A) {
            this.teamAName = name;
            this.teamAShirtColor = shirtColor;
        } else {
            this.teamBName = name;
            this.teamBShirtColor = shirtColor;
        }
    }

    public String getTeamName(MatchTeam team) {
        return team == MatchTeam.A ? teamAName : teamBName;
    }

    public ShirtColor getTeamShirtColor(MatchTeam team) {
        return team == MatchTeam.A ? teamAShirtColor : teamBShirtColor;
    }

    public void changePixKey(String pixKey) {
        this.pixKey = pixKey;
    }

    public Long getId() {
        return id;
    }

    public Reservation getReservation() {
        return reservation;
    }

    public RecurringReservation getRecurringReservation() {
        return recurringReservation;
    }

    public LocalDate getOccurrenceDate() {
        return occurrenceDate;
    }

    public User getOrganizer() {
        return organizer;
    }

    public ScheduleSlot getScheduleSlot() {
        return scheduleSlot;
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public BigDecimal getPrice() {
        return price;
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

    public String getPixKey() {
        return pixKey;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Instant getTeamsUpdatedAt() {
        return teamsUpdatedAt;
    }
}
