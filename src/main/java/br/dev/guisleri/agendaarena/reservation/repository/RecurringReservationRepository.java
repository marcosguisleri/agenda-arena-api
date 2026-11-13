package br.dev.guisleri.agendaarena.reservation.repository;

import br.dev.guisleri.agendaarena.reservation.entity.RecurringReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecurringReservationRepository extends JpaRepository<RecurringReservation, Long> {

    Optional<RecurringReservation> findByIdAndScheduleSlotId(Long id, Long scheduleSlotId);

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
          AND NOT EXISTS (
              SELECT 1
              FROM RecurringReservationRelease rel
              WHERE rel.recurringReservation = r
                AND rel.occurrenceDate = :reservationDate
          )
        """)
    boolean existsCoveringDate(
            @Param("scheduleSlotId") Long scheduleSlotId,
            @Param("reservationDate") LocalDate reservationDate
    );

    @Query("""
        SELECT r
        FROM RecurringReservation r
        JOIN FETCH r.scheduleSlot s
        LEFT JOIN FETCH r.organizer
        WHERE s.sportField.id = :sportFieldId
          AND s.dayOfWeek = :dayOfWeek
          AND r.startDate <= :date
          AND (r.endDate IS NULL OR r.endDate >= :date)
        """)
    List<RecurringReservation> findAllCoveringDateBySportField(
            @Param("sportFieldId") Long sportFieldId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("date") LocalDate date
    );

    @Query("""
        SELECT r.scheduleSlot.id
        FROM RecurringReservation r
        WHERE r.scheduleSlot.sportField.id = :sportFieldId
          AND r.startDate <= :date
          AND (r.endDate IS NULL OR r.endDate >= :date)
          AND NOT EXISTS (
              SELECT 1
              FROM RecurringReservationRelease rel
              WHERE rel.recurringReservation = r
                AND rel.occurrenceDate = :date
          )
        """)
    List<Long> findOccupiedScheduleSlotIds(
            @Param("sportFieldId") Long sportFieldId,
            @Param("date") LocalDate date
    );

}
