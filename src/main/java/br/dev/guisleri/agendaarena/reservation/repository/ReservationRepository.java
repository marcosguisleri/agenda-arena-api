package br.dev.guisleri.agendaarena.reservation.repository;

import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.reservation.model.ReservationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @EntityGraph(attributePaths = {"scheduleSlot", "organizer"})
    Optional<Reservation> findByIdAndScheduleSlotId(Long id, Long scheduleSlotId);

    boolean existsByScheduleSlotIdAndReservationDateAndStatusIn(
            Long scheduleSlotId,
            LocalDate reservationDate,
            Collection<ReservationStatus> statuses
    );

    boolean existsByScheduleSlotIdAndStatusInAndReservationDateBetween(
            Long scheduleSlotId,
            Collection<ReservationStatus> statuses,
            LocalDate startDate,
            LocalDate endDate
    );

    boolean existsByScheduleSlotIdAndStatusInAndReservationDateGreaterThanEqual(
            Long scheduleSlotId,
            Collection<ReservationStatus> statuses,
            LocalDate startDate
    );

    @EntityGraph(attributePaths = "scheduleSlot")
    List<Reservation> findAllByScheduleSlotSportFieldIdAndReservationDateAndStatusIn(
            Long sportFieldId,
            LocalDate reservationDate,
            Collection<ReservationStatus> statuses
    );

    @EntityGraph(attributePaths = "scheduleSlot")
    List<Reservation> findAllByOrganizerIdOrderByReservationDateDescStartTimeDesc(
            Long organizerId
    );

    @EntityGraph(attributePaths = "scheduleSlot")
    Optional<Reservation> findByIdAndOrganizerId(Long id, Long organizerId);

    /**
     * Consultas só de ids: permitem travar o horário ANTES de carregar a reserva, para que o
     * estado lido já seja o confirmado por quem tinha o lock.
     */
    @Query("""
            select r.scheduleSlot.id
            from Reservation r
            where r.id = :reservationId and r.organizer.id = :organizerId
            """)
    Optional<Long> findScheduleSlotIdByIdAndOrganizerId(
            @Param("reservationId") Long reservationId,
            @Param("organizerId") Long organizerId
    );

    @Query("""
            select r.scheduleSlot.sportField.id
            from Reservation r
            where r.id = :reservationId and r.organizer.id = :organizerId
            """)
    Optional<Long> findSportFieldIdByIdAndOrganizerId(
            @Param("reservationId") Long reservationId,
            @Param("organizerId") Long organizerId
    );

}
