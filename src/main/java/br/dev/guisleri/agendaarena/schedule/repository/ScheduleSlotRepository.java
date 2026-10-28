package br.dev.guisleri.agendaarena.schedule.repository;

import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface ScheduleSlotRepository extends JpaRepository<ScheduleSlot, Long> {

    boolean existsBySportFieldIdAndDayOfWeekAndStartTime(Long sportFieldId, DayOfWeek dayOfWeek, LocalTime startTime);

    @EntityGraph(attributePaths = "sportField")
    List<ScheduleSlot> findAllBySportFieldId(Long sportFieldId);

    @EntityGraph(attributePaths = "sportField")
    Optional<ScheduleSlot> findByIdAndSportFieldId(
            Long scheduleSlotId,
            Long sportFieldId
    );

    @EntityGraph(attributePaths = "sportField")
    List<ScheduleSlot> findAllBySportFieldIdAndDayOfWeekAndActiveTrueOrderByStartTime(
            Long sportFieldId,
            DayOfWeek dayOfWeek
    );

    List<ScheduleSlot> findAllBySportFieldIdAndDayOfWeek(
            Long sportFieldId,
            DayOfWeek dayOfWeek
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT s
        FROM ScheduleSlot s
        WHERE s.id = :scheduleSlotId
          AND s.sportField.id = :sportFieldId
        """)
    Optional<ScheduleSlot> findByIdAndSportFieldIdForUpdate(
            @Param("scheduleSlotId") Long scheduleSlotId,
            @Param("sportFieldId") Long sportFieldId
    );

}
