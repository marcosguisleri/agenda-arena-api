package br.dev.guisleri.agendaarena.schedule.entity;

import br.dev.guisleri.agendaarena.sportfield.entity.SportField;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;

@Entity
@Table(name = "schedule_slots")
public class ScheduleSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sport_field_id", nullable = false)
    private SportField sportField;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 9)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private boolean active;

    protected ScheduleSlot() {
    }

    public ScheduleSlot(
            SportField sportField,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            BigDecimal price
    ) {
        this.sportField = sportField;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.price = price;
        this.active = true;
    }

    public LocalTime getEndTime() {
        return startTime.plusMinutes(
                sportField.getSlotDurationMinutes()
        );
    }

    public Long getId() {
        return id;
    }

    public SportField getSportField() {
        return sportField;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Boolean isActive() {
        return active;
    }
}
