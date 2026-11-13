package br.dev.guisleri.agendaarena.match.repository;

import br.dev.guisleri.agendaarena.match.entity.Match;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, Long> {

    /**
     * Carrega tudo o que a página mostra numa consulta só (open-in-view está desligado).
     */
    @EntityGraph(attributePaths = {
            "reservation",
            "reservation.scheduleSlot",
            "reservation.scheduleSlot.sportField",
            "reservation.scheduleSlot.sportField.establishment"
    })
    Optional<Match> findByPublicToken(String publicToken);

    @EntityGraph(attributePaths = {
            "reservation",
            "reservation.scheduleSlot",
            "reservation.scheduleSlot.sportField",
            "reservation.scheduleSlot.sportField.establishment"
    })
    Optional<Match> findByReservationIdAndReservationOrganizerId(
            Long reservationId,
            Long organizerId
    );
}
