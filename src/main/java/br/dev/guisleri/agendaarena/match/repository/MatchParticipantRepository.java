package br.dev.guisleri.agendaarena.match.repository;

import br.dev.guisleri.agendaarena.match.entity.MatchParticipant;
import br.dev.guisleri.agendaarena.match.model.PlayerPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MatchParticipantRepository extends JpaRepository<MatchParticipant, Long> {

    /**
     * Ordem de inscrição (o id desempata inscrições no mesmo instante).
     */
    List<MatchParticipant> findAllByMatchIdOrderByCreatedAtAscIdAsc(Long matchId);

    /**
     * Contagens por partida numa consulta só (listagem do admin). Partida sem participantes não
     * aparece no resultado: quem chama completa com zeros.
     */
    @Query("""
            select new br.dev.guisleri.agendaarena.match.repository.MatchParticipantCounts(
                p.match.id,
                count(case when p.position
                    = br.dev.guisleri.agendaarena.match.model.PlayerPosition.FIELD
                    then 1 end),
                count(case when p.position
                    = br.dev.guisleri.agendaarena.match.model.PlayerPosition.GOALKEEPER
                    then 1 end),
                count(case when p.paysShare = true then 1 end),
                count(case when p.paid = true then 1 end)
            )
            from MatchParticipant p
            where p.match.id in :matchIds
            group by p.match.id
            """)
    List<MatchParticipantCounts> countByMatchIds(@Param("matchIds") Collection<Long> matchIds);

    long countByMatchIdAndPosition(Long matchId, PlayerPosition position);

    Optional<MatchParticipant> findByIdAndMatchId(Long id, Long matchId);
}
