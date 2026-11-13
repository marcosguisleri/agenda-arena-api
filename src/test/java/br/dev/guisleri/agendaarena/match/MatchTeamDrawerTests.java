package br.dev.guisleri.agendaarena.match;

import br.dev.guisleri.agendaarena.match.model.MatchTeam;
import br.dev.guisleri.agendaarena.match.model.PlayerPosition;
import br.dev.guisleri.agendaarena.match.service.MatchTeamDrawer;
import br.dev.guisleri.agendaarena.match.service.MatchTeamDrawer.Candidate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sorteio de times (teste unitário, sem Spring). Sementes fixas: resultados reproduzíveis.
 */
class MatchTeamDrawerTests {

    @ParameterizedTest
    @CsvSource({
            "2, 0",
            "0, 2",
            "1, 1",
            "10, 0",
            "10, 2",
            "11, 1",
            "9, 3",
            "14, 4",
            "3, 4",
            "60, 4"
    })
    void shouldAssignEveryoneAndKeepTeamsAndGoalkeepersBalanced(int fieldPlayers, int goalkeepers) {
        List<Candidate> candidates = candidates(fieldPlayers, goalkeepers);

        for (long seed = 0; seed < 200; seed++) {
            Map<Long, MatchTeam> teams = MatchTeamDrawer.draw(candidates, new Random(seed));

            assertThat(teams).hasSize(candidates.size());
            assertThat(teams.keySet())
                    .containsExactlyInAnyOrderElementsOf(
                            candidates.stream().map(Candidate::id).toList());

            long sizeA = teams.values().stream().filter(team -> team == MatchTeam.A).count();
            long goalkeepersA = candidates.stream()
                    .filter(candidate -> candidate.position() == PlayerPosition.GOALKEEPER)
                    .filter(candidate -> teams.get(candidate.id()) == MatchTeam.A)
                    .count();

            assertThat(Math.abs(2 * sizeA - candidates.size())).isLessThanOrEqualTo(1);
            assertThat(Math.abs(2 * goalkeepersA - goalkeepers)).isLessThanOrEqualTo(1);
        }
    }

    @Test
    void shouldBeReproducibleWithTheSameSeed() {
        List<Candidate> candidates = candidates(10, 2);

        assertThat(MatchTeamDrawer.draw(candidates, new Random(42)))
                .isEqualTo(MatchTeamDrawer.draw(candidates, new Random(42)));
    }

    @Test
    void shouldProduceDifferentTeamsAcrossDraws() {
        List<Candidate> candidates = candidates(10, 2);
        Random random = new Random(7);
        Set<Map<Long, MatchTeam>> outcomes = new HashSet<>();

        for (int i = 0; i < 20; i++) {
            outcomes.add(MatchTeamDrawer.draw(candidates, random));
        }

        assertThat(outcomes).hasSizeGreaterThan(1);
    }

    @Test
    void shouldReturnNoTeamsWithoutCandidates() {
        assertThat(MatchTeamDrawer.draw(List.of(), new Random(1))).isEmpty();
    }

    private static List<Candidate> candidates(int fieldPlayers, int goalkeepers) {
        List<Candidate> candidates = new ArrayList<>();
        long id = 1;

        for (int i = 0; i < fieldPlayers; i++) {
            candidates.add(new Candidate(id++, PlayerPosition.FIELD));
        }
        for (int i = 0; i < goalkeepers; i++) {
            candidates.add(new Candidate(id++, PlayerPosition.GOALKEEPER));
        }

        return candidates;
    }
}
