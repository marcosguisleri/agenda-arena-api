package br.dev.guisleri.agendaarena.match.repository;

import br.dev.guisleri.agendaarena.match.entity.Match;
import jakarta.persistence.LockModeType;
import br.dev.guisleri.agendaarena.match.model.MatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, Long> {

    /**
     * Carrega tudo o que a página mostra numa consulta só (open-in-view está desligado).
     */
    @EntityGraph(attributePaths = {
            "reservation",
            "recurringReservation",
            "scheduleSlot",
            "scheduleSlot.sportField",
            "scheduleSlot.sportField.establishment"
    })
    Optional<Match> findByPublicToken(String publicToken);

    @EntityGraph(attributePaths = {
            "reservation",
            "recurringReservation",
            "scheduleSlot",
            "scheduleSlot.sportField",
            "scheduleSlot.sportField.establishment"
    })
    Optional<Match> findByReservationIdAndOrganizerId(
            Long reservationId,
            Long organizerId
    );

    @EntityGraph(attributePaths = {
            "reservation",
            "recurringReservation",
            "scheduleSlot",
            "scheduleSlot.sportField",
            "scheduleSlot.sportField.establishment"
    })
    Optional<Match> findByIdAndOrganizerId(Long id, Long organizerId);

    /**
     * Partida de uma ocorrência de série do organizador (de outro: vazio, vira 404).
     */
    @EntityGraph(attributePaths = {
            "reservation",
            "recurringReservation",
            "scheduleSlot",
            "scheduleSlot.sportField",
            "scheduleSlot.sportField.establishment"
    })
    Optional<Match> findByRecurringReservationIdAndOccurrenceDateAndOrganizerId(
            Long recurringReservationId,
            LocalDate occurrenceDate,
            Long organizerId
    );

    /**
     * Partida pelo id dentro de um estabelecimento (visão do admin). De outro estabelecimento:
     * vazio, vira 404.
     */
    @EntityGraph(attributePaths = {
            "reservation",
            "recurringReservation",
            "scheduleSlot",
            "scheduleSlot.sportField",
            "organizer"
    })
    @Query("""
            select m from Match m
            where m.id = :matchId
              and m.scheduleSlot.sportField.establishment.id = :establishmentId
            """)
    Optional<Match> findByIdAndEstablishmentId(
            @Param("matchId") Long matchId,
            @Param("establishmentId") Long establishmentId
    );

    /**
     * Listagem do administrador: partidas de um estabelecimento num período, com filtros
     * opcionais, em ordem fixa (data, horário, id).
     */
    @EntityGraph(attributePaths = {
            "reservation",
            "recurringReservation",
            "scheduleSlot",
            "scheduleSlot.sportField",
            "organizer"
    })
    @Query(value = """
            select m from Match m
            where m.scheduleSlot.sportField.establishment.id = :establishmentId
              and m.date between :from and :to
              and m.status in :statuses
              and (:sportFieldId is null or m.scheduleSlot.sportField.id = :sportFieldId)
            order by m.date, m.startTime, m.id
            """,
            countQuery = """
            select count(m) from Match m
            where m.scheduleSlot.sportField.establishment.id = :establishmentId
              and m.date between :from and :to
              and m.status in :statuses
              and (:sportFieldId is null or m.scheduleSlot.sportField.id = :sportFieldId)
            """)
    Page<Match> findForAdmin(
            @Param("establishmentId") Long establishmentId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("statuses") Collection<MatchStatus> statuses,
            @Param("sportFieldId") Long sportFieldId,
            Pageable pageable
    );

    /**
     * Lock pessimista da partida do organizador: serializa inscrições e alterações da lista,
     * rateio e times. O dono está na própria linha ({@code organizer_id}), então o lock não se
     * estende a outra tabela.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Match m where m.id = :matchId and m.organizer.id = :organizerId")
    Optional<Match> findByIdAndOrganizerIdForUpdate(
            @Param("matchId") Long matchId,
            @Param("organizerId") Long organizerId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Match m where m.publicToken = :publicToken")
    Optional<Match> findByPublicTokenForUpdate(@Param("publicToken") String publicToken);

    /**
     * Partidas das ocorrências de uma série num intervalo de datas.
     */
    List<Match> findAllByRecurringReservationIdAndOccurrenceDateBetween(
            Long recurringReservationId,
            LocalDate fromDate,
            LocalDate toDate
    );

    boolean existsByRecurringReservationId(Long recurringReservationId);
}
