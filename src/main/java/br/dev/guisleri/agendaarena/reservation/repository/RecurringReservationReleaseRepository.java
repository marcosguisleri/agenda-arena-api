package br.dev.guisleri.agendaarena.reservation.repository;

import br.dev.guisleri.agendaarena.reservation.entity.RecurringReservationRelease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface RecurringReservationReleaseRepository extends JpaRepository<RecurringReservationRelease, Long> {

    boolean existsByRecurringReservationIdAndOccurrenceDate(Long recurringReservationId, LocalDate occurrenceDate);

    @Query("""
        SELECT rel.recurringReservation.id
        FROM RecurringReservationRelease rel
        WHERE rel.occurrenceDate = :occurrenceDate
          AND rel.recurringReservation.id IN :recurringReservationIds
        """)
    List<Long> findReleasedRecurringReservationIds(
            @Param("recurringReservationIds") Collection<Long> recurringReservationIds,
            @Param("occurrenceDate") LocalDate occurrenceDate
    );

    /**
     * Datas liberadas de uma série num intervalo (listagem de ocorrências, sem N+1).
     */
    @Query("""
        SELECT rel.occurrenceDate
        FROM RecurringReservationRelease rel
        WHERE rel.recurringReservation.id = :recurringReservationId
          AND rel.occurrenceDate BETWEEN :fromDate AND :toDate
        """)
    List<LocalDate> findReleasedDates(
            @Param("recurringReservationId") Long recurringReservationId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );
}
