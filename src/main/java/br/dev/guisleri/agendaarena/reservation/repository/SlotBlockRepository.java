package br.dev.guisleri.agendaarena.reservation.repository;

import br.dev.guisleri.agendaarena.reservation.entity.SlotBlock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SlotBlockRepository extends JpaRepository<SlotBlock, Long> {

    boolean existsByScheduleSlotIdAndBlockDate(Long scheduleSlotId, LocalDate blockDate);

    boolean existsByScheduleSlotIdAndBlockDateGreaterThanEqual(
            Long scheduleSlotId,
            LocalDate startDate
    );

    boolean existsByScheduleSlotIdAndBlockDateBetween(
            Long scheduleSlotId,
            LocalDate startDate,
            LocalDate endDate
    );

    @EntityGraph(attributePaths = "scheduleSlot")
    Optional<SlotBlock> findByIdAndScheduleSlotId(Long id, Long scheduleSlotId);

    @EntityGraph(attributePaths = "scheduleSlot")
    List<SlotBlock> findAllByScheduleSlotIdAndBlockDateGreaterThanEqualOrderByBlockDate(
            Long scheduleSlotId,
            LocalDate from
    );

    @EntityGraph(attributePaths = "scheduleSlot")
    List<SlotBlock> findAllByScheduleSlotSportFieldIdAndBlockDate(
            Long sportFieldId,
            LocalDate blockDate
    );

    @Query("""
        SELECT b.scheduleSlot.id
        FROM SlotBlock b
        WHERE b.scheduleSlot.sportField.id = :sportFieldId
          AND b.blockDate = :date
        """)
    List<Long> findBlockedScheduleSlotIds(
            @Param("sportFieldId") Long sportFieldId,
            @Param("date") LocalDate date
    );

    /**
     * Listagem do administrador: bloqueios de um estabelecimento num período, com filtro opcional
     * de campo, em ordem fixa (data, horário, id). Horário e campo vêm na mesma consulta.
     */
    @EntityGraph(attributePaths = {"scheduleSlot", "scheduleSlot.sportField"})
    @Query(value = """
            select b from SlotBlock b
            where b.scheduleSlot.sportField.establishment.id = :establishmentId
              and b.blockDate between :from and :to
              and (:sportFieldId is null or b.scheduleSlot.sportField.id = :sportFieldId)
            order by b.blockDate, b.scheduleSlot.startTime, b.id
            """,
            countQuery = """
            select count(b) from SlotBlock b
            where b.scheduleSlot.sportField.establishment.id = :establishmentId
              and b.blockDate between :from and :to
              and (:sportFieldId is null or b.scheduleSlot.sportField.id = :sportFieldId)
            """)
    Page<SlotBlock> findForAdmin(
            @Param("establishmentId") Long establishmentId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("sportFieldId") Long sportFieldId,
            Pageable pageable
    );
}
