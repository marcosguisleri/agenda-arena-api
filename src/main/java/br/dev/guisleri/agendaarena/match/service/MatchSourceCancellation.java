package br.dev.guisleri.agendaarena.match.service;

import br.dev.guisleri.agendaarena.match.entity.Match;
import br.dev.guisleri.agendaarena.reservation.entity.RecurringReservation;
import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.reservation.model.ReservationStatus;
import br.dev.guisleri.agendaarena.reservation.repository.RecurringReservationReleaseRepository;
import br.dev.guisleri.agendaarena.reservation.repository.ReleasedOccurrence;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Único lugar que decide se o horário de origem da partida deixou de existir. Origem reserva:
 * reserva cancelada. Origem ocorrência de recorrência: data liberada. A versão em lote dá a mesma
 * resposta para uma página de partidas com uma consulta só.
 */
@Component
public class MatchSourceCancellation {

    private final RecurringReservationReleaseRepository releaseRepository;

    public MatchSourceCancellation(RecurringReservationReleaseRepository releaseRepository) {
        this.releaseRepository = releaseRepository;
    }

    public boolean isCancelled(Match match) {
        Reservation reservation = match.getReservation();

        if (reservation != null) {
            return reservation.getStatus() == ReservationStatus.CANCELLED;
        }

        RecurringReservation series = match.getRecurringReservation();

        return series != null
                && releaseRepository.existsByRecurringReservationIdAndOccurrenceDate(
                series.getId(),
                match.getOccurrenceDate()
        );
    }

    /**
     * Ids das partidas canceladas dentre as informadas. As reservas e séries precisam estar
     * carregadas (graph); as liberações das ocorrências vêm numa consulta só.
     */
    public Set<Long> cancelledMatchIds(Collection<Match> matches) {
        Set<Long> cancelled = new HashSet<>();
        List<Match> occurrenceMatches = matches.stream()
                .filter(match -> match.getReservation() == null
                        && match.getRecurringReservation() != null)
                .toList();

        for (Match match : matches) {
            Reservation reservation = match.getReservation();

            if (reservation != null && reservation.getStatus() == ReservationStatus.CANCELLED) {
                cancelled.add(match.getId());
            }
        }

        if (occurrenceMatches.isEmpty()) {
            return cancelled;
        }

        LocalDate from = occurrenceMatches.stream()
                .map(Match::getOccurrenceDate)
                .min(LocalDate::compareTo)
                .orElseThrow();
        LocalDate to = occurrenceMatches.stream()
                .map(Match::getOccurrenceDate)
                .max(LocalDate::compareTo)
                .orElseThrow();
        Set<ReleasedOccurrence> released = new HashSet<>(
                releaseRepository.findReleasedOccurrences(
                        occurrenceMatches.stream()
                                .map(match -> match.getRecurringReservation().getId())
                                .collect(Collectors.toSet()),
                        from,
                        to
                )
        );

        for (Match match : occurrenceMatches) {
            if (released.contains(new ReleasedOccurrence(
                    match.getRecurringReservation().getId(),
                    match.getOccurrenceDate()
            ))) {
                cancelled.add(match.getId());
            }
        }

        return cancelled;
    }
}
