package br.dev.guisleri.agendaarena.schedule.repository;

import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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

}
