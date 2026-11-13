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

    @EntityGraph(attributePaths = {
            "reservation",
            "scheduleSlot",
            "scheduleSlot.sportField",
            "scheduleSlot.sportField.establishment"
    })
    Optional<Match> findByIdAndOrganizerId(Long id, Long organizerId);

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
}
