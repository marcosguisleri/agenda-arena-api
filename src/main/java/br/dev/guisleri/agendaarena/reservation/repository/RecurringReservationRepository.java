package br.dev.guisleri.agendaarena.reservation.repository;

import br.dev.guisleri.agendaarena.reservation.entity.RecurringReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface RecurringReservationRepository extends JpaRepository<RecurringReservation, Long> {

    @Query("SELECT COUNT(r) > 0 FROM RecurringReservation r " +
            "WHERE r.scheduleSlot.id = :scheduleSlotId " +
            "AND r.startDate <= COALESCE(:newEndDate, r.startDate) " +
            "AND (r.endDate IS NULL OR r.endDate >= :newStartDate)")
    boolean existsOverlapping(
            @Param("scheduleSlotId") Long scheduleSlotId,
            @Param("newStartDate") LocalDate newStartDate,
            @Param("newEndDate") LocalDate newEndDate
    );

    @Query("""
        SELECT COUNT(r) > 0
        FROM RecurringReservation r
        WHERE r.scheduleSlot.id = :scheduleSlotId
          AND r.startDate <= :reservationDate
          AND (r.endDate IS NULL OR r.endDate >= :reservationDate)
        """)
    boolean existsCoveringDate(
            @Param("scheduleSlotId") Long scheduleSlotId,
            @Param("reservationDate") LocalDate reservationDate
    );

    @Query("""
        SELECT r.scheduleSlot.id
        FROM RecurringReservation r
        WHERE r.scheduleSlot.sportField.id = :sportFieldId
          AND r.startDate <= :date
          AND (r.endDate IS NULL OR r.endDate >= :date)
        """)
    List<Long> findOccupiedScheduleSlotIds(
            @Param("sportFieldId") Long sportFieldId,
            @Param("date") LocalDate date
    );

}
