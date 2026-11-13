package br.dev.guisleri.agendaarena.match.repository;

import br.dev.guisleri.agendaarena.match.entity.Match;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, Long> {

    /**
     * Carrega tudo o que a página mostra numa consulta só (open-in-view está desligado).
     */
    @EntityGraph(attributePaths = {
            "reservation",
            "scheduleSlot",
            "scheduleSlot.sportField",
            "scheduleSlot.sportField.establishment"
    })
    Optional<Match> findByPublicToken(String publicToken);

    @EntityGraph(attributePaths = {
            "reservation",
            "scheduleSlot",
            "scheduleSlot.sportField",
            "scheduleSlot.sportField.establishment"
    })
    Optional<Match> findByReservationIdAndOrganizerId(
            Long reservationId,
            Long organizerId
    );

    /**
     * Só o id, sem lock: permite travar depois apenas a linha de {@code matches}. O dono é o
     * organizador copiado na partida, sem join com a origem.
     */
    @Query("""
            select m.id
            from Match m
            where m.reservation.id = :reservationId
              and m.organizer.id = :organizerId
            """)
    Optional<Long> findIdByReservationIdAndOrganizerId(
            @Param("reservationId") Long reservationId,
            @Param("organizerId") Long organizerId
    );

    /**
     * Lock pessimista da partida: serializa inscrições e alterações da lista (limite de goleiros,
     * estado aberto) e, nas próximas fatias, o fechamento.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Match m where m.id = :matchId")
    Optional<Match> findByIdForUpdate(@Param("matchId") Long matchId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Match m where m.publicToken = :publicToken")
    Optional<Match> findByPublicTokenForUpdate(@Param("publicToken") String publicToken);
}
