package br.dev.guisleri.agendaarena.match.repository;

import br.dev.guisleri.agendaarena.match.entity.MatchParticipant;
import br.dev.guisleri.agendaarena.match.model.PlayerPosition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MatchParticipantRepository extends JpaRepository<MatchParticipant, Long> {

    /**
     * Ordem de inscrição (o id desempata inscrições no mesmo instante).
     */
    List<MatchParticipant> findAllByMatchIdOrderByCreatedAtAscIdAsc(Long matchId);

    long countByMatchIdAndPosition(Long matchId, PlayerPosition position);

    Optional<MatchParticipant> findByIdAndMatchId(Long id, Long matchId);
}
