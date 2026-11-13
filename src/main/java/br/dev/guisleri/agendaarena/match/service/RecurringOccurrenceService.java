package br.dev.guisleri.agendaarena.match.service;

import br.dev.guisleri.agendaarena.match.dto.RecurringOccurrenceResponseDTO;
import br.dev.guisleri.agendaarena.match.entity.Match;
import br.dev.guisleri.agendaarena.match.repository.MatchRepository;
import br.dev.guisleri.agendaarena.reservation.entity.RecurringReservation;
import br.dev.guisleri.agendaarena.reservation.exception.RecurringReservationNotAllowedException;
import br.dev.guisleri.agendaarena.reservation.repository.RecurringReservationReleaseRepository;
import br.dev.guisleri.agendaarena.reservation.service.RecurringReservationService;
import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Próximas datas de uma série recorrente do organizador, com o estado de cada uma (liberada,
 * com partida). Leitura sem lock; a criação da partida da ocorrência (que trava o slot) está em
 * {@link MatchService#createOccurrenceMatch}.
 */
@Service
public class RecurringOccurrenceService {

    public static final int DEFAULT_WEEKS = 8;
    public static final int MAX_WEEKS = 26;

    private final RecurringReservationService recurringReservationService;
    private final RecurringReservationReleaseRepository releaseRepository;
    private final MatchRepository matchRepository;
    private final Clock clock;

    public RecurringOccurrenceService(
            RecurringReservationService recurringReservationService,
            RecurringReservationReleaseRepository releaseRepository,
            MatchRepository matchRepository,
            Clock clock
    ) {
        this.recurringReservationService = recurringReservationService;
        this.releaseRepository = releaseRepository;
        this.matchRepository = matchRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RecurringOccurrenceResponseDTO> findUpcomingOccurrences(
            Long organizerId,
            Long recurringReservationId,
            int weeks
    ) {
        if (weeks < 1 || weeks > MAX_WEEKS) {
            throw new RecurringReservationNotAllowedException(
                    "weeks must be between 1 and " + MAX_WEEKS
            );
        }

        RecurringReservation recurringReservation =
                recurringReservationService.findOrganizerRecurringReservation(
                        organizerId,
                        recurringReservationId
                );

        List<LocalDate> dates = upcomingDates(recurringReservation, weeks);

        if (dates.isEmpty()) {
            return List.of();
        }

        LocalDate from = dates.getFirst();
        LocalDate to = dates.getLast();

        Set<LocalDate> released = new HashSet<>(
                releaseRepository.findReleasedDates(recurringReservationId, from, to)
        );

        Map<LocalDate, Match> matchesByDate = new HashMap<>();
        matchRepository
                .findAllByRecurringReservationIdAndOccurrenceDateBetween(
                        recurringReservationId,
                        from,
                        to
                )
                .forEach(match -> matchesByDate.put(match.getOccurrenceDate(), match));

        return dates.stream()
                .map(date -> {
                    Match match = matchesByDate.get(date);

                    return new RecurringOccurrenceResponseDTO(
                            date,
                            released.contains(date),
                            match != null ? match.getId() : null,
                            match != null ? match.getPublicToken() : null
                    );
                })
                .toList();
    }

    /**
     * Até {@code weeks} datas a partir de hoje, dentro do período da série. Hoje só entra se o
     * horário ainda não começou.
     */
    private List<LocalDate> upcomingDates(RecurringReservation recurringReservation, int weeks) {
        ScheduleSlot slot = recurringReservation.getScheduleSlot();
        ZonedDateTime now = ZonedDateTime.now(clock);
        LocalDate today = now.toLocalDate();

        LocalDate first = today.isAfter(recurringReservation.getStartDate())
                ? today
                : recurringReservation.getStartDate();
        first = first.with(TemporalAdjusters.nextOrSame(slot.getDayOfWeek()));

        if (first.equals(today) && !slot.getStartTime().isAfter(now.toLocalTime())) {
            first = first.plusWeeks(1);
        }

        List<LocalDate> dates = new ArrayList<>();

        for (int week = 0; week < weeks; week++) {
            LocalDate date = first.plusWeeks(week);

            if (recurringReservation.getEndDate() != null
                    && date.isAfter(recurringReservation.getEndDate())) {
                break;
            }

            dates.add(date);
        }

        return dates;
    }
}
