package br.dev.guisleri.agendaarena.match.repository;

/**
 * Contagens de participantes de uma partida, calculadas no banco para a listagem do admin.
 */
public record MatchParticipantCounts(
        Long matchId,
        long fieldPlayers,
        long goalkeepers,
        long paying,
        long paid
) {

    public static MatchParticipantCounts empty(Long matchId) {
        return new MatchParticipantCounts(matchId, 0, 0, 0, 0);
    }
}
