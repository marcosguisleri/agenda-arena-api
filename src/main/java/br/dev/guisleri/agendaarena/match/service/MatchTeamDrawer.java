package br.dev.guisleri.agendaarena.match.service;

import br.dev.guisleri.agendaarena.match.model.MatchTeam;
import br.dev.guisleri.agendaarena.match.model.PlayerPosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * Sorteio aleatório simples de 2 times (sem balanceamento por habilidade).
 * <p>
 * Goleiros embaralhados e alternados entre os times; depois, cada jogador de linha embaralhado vai
 * para o time com menos gente (empate decidido no sorteio). Garante: tamanhos dos times diferem em
 * no máximo 1, e a quantidade de goleiros também.
 */
public final class MatchTeamDrawer {

    public record Candidate(Long id, PlayerPosition position) {
    }

    private MatchTeamDrawer() {
    }

    public static Map<Long, MatchTeam> draw(List<Candidate> candidates, RandomGenerator random) {
        List<Candidate> goalkeepers = new ArrayList<>();
        List<Candidate> fieldPlayers = new ArrayList<>();

        for (Candidate candidate : candidates) {
            if (candidate.position() == PlayerPosition.GOALKEEPER) {
                goalkeepers.add(candidate);
            } else {
                fieldPlayers.add(candidate);
            }
        }

        Collections.shuffle(goalkeepers, random);
        Collections.shuffle(fieldPlayers, random);

        Map<Long, MatchTeam> teams = new LinkedHashMap<>();
        int sizeA = 0;
        int sizeB = 0;

        MatchTeam next = random.nextBoolean() ? MatchTeam.A : MatchTeam.B;

        for (Candidate goalkeeper : goalkeepers) {
            teams.put(goalkeeper.id(), next);

            if (next == MatchTeam.A) {
                sizeA++;
                next = MatchTeam.B;
            } else {
                sizeB++;
                next = MatchTeam.A;
            }
        }

        for (Candidate fieldPlayer : fieldPlayers) {
            MatchTeam team;

            if (sizeA != sizeB) {
                team = sizeA < sizeB ? MatchTeam.A : MatchTeam.B;
            } else {
                team = random.nextBoolean() ? MatchTeam.A : MatchTeam.B;
            }

            teams.put(fieldPlayer.id(), team);

            if (team == MatchTeam.A) {
                sizeA++;
            } else {
                sizeB++;
            }
        }

        return teams;
    }
}
