package br.dev.guisleri.agendaarena.reservation.entity;

import br.dev.guisleri.agendaarena.reservation.exception.RecurringReservationNotAllowedException;
import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import br.dev.guisleri.agendaarena.user.entity.User;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "recurring_reservations")
public class RecurringReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_slot_id", nullable = false)
    private ScheduleSlot scheduleSlot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id")
    private User organizer;

    @Column(name = "responsible_name", nullable = false, length = 120)
    private String responsibleName;

    @Column(name = "responsible_phone", nullable = false, length = 20)
    private String responsiblePhone;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    protected RecurringReservation() {
    }

    public RecurringReservation(
            ScheduleSlot scheduleSlot,
            User organizer,
            String responsibleName,
            String responsiblePhone,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (scheduleSlot == null) {
            throw new RecurringReservationNotAllowedException(
                    "Schedule slot is null"
            );
        }

        this.scheduleSlot = scheduleSlot;

        this.organizer = organizer;

        if (responsibleName == null || responsibleName.isBlank()) {
            throw new RecurringReservationNotAllowedException(
                    "The name of the person in charge cannot be empty"
            );
        }

        this.responsibleName = responsibleName;

        if (responsiblePhone == null || responsiblePhone.isBlank()) {
            throw new RecurringReservationNotAllowedException(
                    "The person in charge's phone number cannot be empty"
            );
        }

        this.responsiblePhone = responsiblePhone;

        if (startDate == null) {
            throw new RecurringReservationNotAllowedException(
                    "The start date cannot be empty"
            );
        }

        if (!startDate.getDayOfWeek().equals(scheduleSlot.getDayOfWeek())) {
            throw new RecurringReservationNotAllowedException(
                    "Start date must match the schedule slot day of week"
            );
        }

        this.startDate = startDate;

        if (endDate != null && endDate.isBefore(startDate)) {
            throw new RecurringReservationNotAllowedException(
                    "End date cannot be before start date"
            );
        }

        this.endDate = endDate;
    }

    public void linkOrganizer(User organizer) {
        this.organizer = organizer;
    }

    public Long getId() {
        return id;
    }

    public String getResponsibleName() {
        return responsibleName;
    }

    public String getResponsiblePhone() {
        return responsiblePhone;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public User getOrganizer() {
        return organizer;
    }

    public ScheduleSlot getScheduleSlot() {
        return scheduleSlot;
    }

}
