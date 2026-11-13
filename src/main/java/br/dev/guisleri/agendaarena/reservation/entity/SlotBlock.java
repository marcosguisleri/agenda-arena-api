package br.dev.guisleri.agendaarena.reservation.entity;

import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Bloqueio administrativo de um slot em uma data (manutenção, evento, indisponibilidade).
 * Não está associado a nenhum cliente e torna o horário indisponível ao público.
 */
@Entity
@Table(name = "slot_blocks")
public class SlotBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_slot_id", nullable = false)
    private ScheduleSlot scheduleSlot;

    @Column(name = "block_date", nullable = false)
    private LocalDate blockDate;

    @Column(length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SlotBlock() {
    }

    public SlotBlock(
            ScheduleSlot scheduleSlot,
            LocalDate blockDate,
            String reason,
            Instant createdAt
    ) {
        this.scheduleSlot = scheduleSlot;
        this.blockDate = blockDate;
        this.reason = reason == null || reason.isBlank() ? null : reason.trim();
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public ScheduleSlot getScheduleSlot() {
        return scheduleSlot;
    }

    public LocalDate getBlockDate() {
        return blockDate;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
