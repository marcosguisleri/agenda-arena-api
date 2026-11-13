package br.dev.guisleri.agendaarena.reservation.entity;


import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "recurring_reservations_releases")
public class RecurringReservationRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recurring_reservation_id", nullable = false)
    private RecurringReservation recurringReservation;

    @Column(name = "occurrence_date", nullable = false)
    private LocalDate occurrenceDate;

    @Column(name = "released_at", nullable = false)
    private Instant releasedAt;

    protected RecurringReservationRelease() {
    }

    public RecurringReservationRelease(RecurringReservation recurringReservation, LocalDate occurrenceDate, Instant releasedAt) {
        this.recurringReservation = recurringReservation;
        this.occurrenceDate = occurrenceDate;
        this.releasedAt = releasedAt;
    }

    public Long getId() {
        return id;
    }

    public RecurringReservation getRecurringReservation() {
        return recurringReservation;
    }

    public LocalDate getOccurrenceDate() {
        return occurrenceDate;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }
}
