package br.dev.guisleri.agendaarena.reservation.entity;

import br.dev.guisleri.agendaarena.reservation.model.ReservationOrigin;
import br.dev.guisleri.agendaarena.reservation.model.ReservationStatus;
import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import br.dev.guisleri.agendaarena.user.entity.User;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id")
    private User organizer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_slot_id", nullable = false)
    private ScheduleSlot scheduleSlot;

    @Column(name = "reservation_date", nullable = false)
    private LocalDate reservationDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationOrigin origin;

    @Column(name = "customer_name", length = 120)
    private String customerName;

    @Column(name = "customer_phone", length = 20)
    private String customerPhone;

    protected Reservation() {
    }

    private Reservation(
            ScheduleSlot scheduleSlot,
            LocalDate reservationDate
    ) {
        this.scheduleSlot = scheduleSlot;
        this.reservationDate = reservationDate;

        this.startTime = scheduleSlot.getStartTime();
        this.endTime = scheduleSlot.getEndTime();
        this.price = scheduleSlot.getPrice();

        this.status = ReservationStatus.CONFIRMED;
    }

    public static Reservation online(
            User organizer,
            ScheduleSlot scheduleSlot,
            LocalDate reservationDate
    ) {
        Reservation reservation =
                new Reservation(scheduleSlot, reservationDate);

        reservation.organizer = organizer;
        reservation.origin = ReservationOrigin.ONLINE;

        return reservation;
    }

    public static Reservation manual(
            ScheduleSlot scheduleSlot,
            LocalDate reservationDate,
            String customerName,
            String customerPhone
    ) {
        Reservation reservation =
                new Reservation(scheduleSlot, reservationDate);

        reservation.customerName = customerName;
        reservation.customerPhone = customerPhone;

        reservation.origin = ReservationOrigin.MANUAL;

        return reservation;
    }

    public Long getId() {
        return id;
    }

    public User getOrganizer() {
        return organizer;
    }

    public ScheduleSlot getScheduleSlot() {
        return scheduleSlot;
    }

    public LocalDate getReservationDate() {
        return reservationDate;
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

    public ReservationStatus getStatus() {
        return status;
    }

    public ReservationOrigin getOrigin() {
        return origin;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }
}
