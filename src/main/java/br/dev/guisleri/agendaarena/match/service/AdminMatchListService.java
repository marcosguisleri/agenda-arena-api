package br.dev.guisleri.agendaarena.match.service;

import br.dev.guisleri.agendaarena.common.dto.PageRequests;
import br.dev.guisleri.agendaarena.common.dto.PageResponseDTO;
import br.dev.guisleri.agendaarena.common.exception.ValidationFailedException;
import br.dev.guisleri.agendaarena.establishment.service.EstablishmentService;
import br.dev.guisleri.agendaarena.match.dto.AdminMatchSummaryResponseDTO;
import br.dev.guisleri.agendaarena.match.entity.Match;
import br.dev.guisleri.agendaarena.match.model.MatchStatus;
import br.dev.guisleri.agendaarena.match.repository.MatchParticipantCounts;
import br.dev.guisleri.agendaarena.match.repository.MatchParticipantRepository;
import br.dev.guisleri.agendaarena.match.repository.MatchRepository;
import br.dev.guisleri.agendaarena.sportfield.service.SportFieldService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Listagem de partidas do estabelecimento para o painel do administrador. Só leitura: não trava
 * nada nem altera nada. As contagens e a situação de cancelamento vêm em lote para a página, sem
 * consulta por partida.
 */
@Service
public class AdminMatchListService {

    public static final int DEFAULT_RANGE_DAYS = 30;
    public static final int MAX_RANGE_DAYS = 92;

    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final MatchSourceCancellation sourceCancellation;
    private final EstablishmentService establishmentService;
    private final SportFieldService sportFieldService;
    private final Clock clock;

    public AdminMatchListService(
            MatchRepository matchRepository,
            MatchParticipantRepository matchParticipantRepository,
            MatchSourceCancellation sourceCancellation,
            EstablishmentService establishmentService,
            SportFieldService sportFieldService,
            Clock clock
    ) {
        this.matchRepository = matchRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.sourceCancellation = sourceCancellation;
        this.establishmentService = establishmentService;
        this.sportFieldService = sportFieldService;
        this.clock = clock;
    }

    /**
     * Sem {@code from}/{@code to}: de hoje a hoje+30. Só um dos dois: o outro fica 30 dias
     * adiante (ou atrás). Período máximo de 92 dias. Sem {@code statuses}: todos.
     */
    @Transactional(readOnly = true)
    public PageResponseDTO<AdminMatchSummaryResponseDTO> list(
            Long establishmentId,
            LocalDate from,
            LocalDate to,
            Long sportFieldId,
            Collection<MatchStatus> statuses,
            int page,
            int size
    ) {
        establishmentService.findEstablishmentById(establishmentId);

        if (sportFieldId != null) {
            sportFieldService.findSportFieldById(establishmentId, sportFieldId);
        }

        LocalDate resolvedFrom = from != null
                ? from
                : to != null ? to.minusDays(DEFAULT_RANGE_DAYS) : LocalDate.now(clock);
        LocalDate resolvedTo = to != null
                ? to
                : resolvedFrom.plusDays(DEFAULT_RANGE_DAYS);

        if (resolvedTo.isBefore(resolvedFrom)) {
            throw new ValidationFailedException("to", "Date 'to' cannot be before 'from'");
        }

        if (resolvedTo.isAfter(resolvedFrom.plusDays(MAX_RANGE_DAYS - 1L))) {
            throw new ValidationFailedException(
                    "to",
                    "Period cannot exceed " + MAX_RANGE_DAYS + " days"
            );
        }

        Collection<MatchStatus> effectiveStatuses =
                statuses == null || statuses.isEmpty()
                        ? EnumSet.allOf(MatchStatus.class)
                        : statuses;

        Page<Match> matches = matchRepository.findForAdmin(
                establishmentId,
                resolvedFrom,
                resolvedTo,
                effectiveStatuses,
                sportFieldId,
                PageRequests.of(page, size)
        );

        List<Match> content = matches.getContent();
        Map<Long, MatchParticipantCounts> counts = content.isEmpty()
                ? Map.of()
                : matchParticipantRepository
                .countByMatchIds(content.stream().map(Match::getId).toList())
                .stream()
                .collect(Collectors.toMap(
                        MatchParticipantCounts::matchId,
                        Function.identity()
                ));
        Set<Long> cancelled = sourceCancellation.cancelledMatchIds(content);

        return PageResponseDTO.from(
                matches,
                match -> AdminMatchSummaryResponseDTO.from(
                        match,
                        cancelled.contains(match.getId()),
                        counts.getOrDefault(
                                match.getId(),
                                MatchParticipantCounts.empty(match.getId())
                        )
                )
        );
    }
}
