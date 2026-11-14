package br.dev.guisleri.agendaarena.reservation.service;

import br.dev.guisleri.agendaarena.common.dto.PageRequests;
import br.dev.guisleri.agendaarena.common.dto.PageResponseDTO;
import br.dev.guisleri.agendaarena.establishment.service.EstablishmentService;
import br.dev.guisleri.agendaarena.reservation.dto.AdminRecurringReservationResponseDTO;
import br.dev.guisleri.agendaarena.reservation.model.RecurringReservationListStatus;
import br.dev.guisleri.agendaarena.reservation.repository.RecurringReservationRepository;
import br.dev.guisleri.agendaarena.sportfield.service.SportFieldService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * Listagem de séries recorrentes do estabelecimento para o painel do administrador. Só leitura.
 */
@Service
public class AdminRecurringReservationListService {

    private static final Set<RecurringReservationListStatus> DEFAULT_STATUSES =
            EnumSet.of(
                    RecurringReservationListStatus.ACTIVE,
                    RecurringReservationListStatus.UPCOMING
            );

    private final RecurringReservationRepository recurringReservationRepository;
    private final EstablishmentService establishmentService;
    private final SportFieldService sportFieldService;
    private final Clock clock;

    public AdminRecurringReservationListService(
            RecurringReservationRepository recurringReservationRepository,
            EstablishmentService establishmentService,
            SportFieldService sportFieldService,
            Clock clock
    ) {
        this.recurringReservationRepository = recurringReservationRepository;
        this.establishmentService = establishmentService;
        this.sportFieldService = sportFieldService;
        this.clock = clock;
    }

    /**
     * Sem {@code statuses}: séries em andamento e futuras (as encerradas só com pedido
     * explícito). {@code hasOrganizer} nulo não filtra.
     */
    @Transactional(readOnly = true)
    public PageResponseDTO<AdminRecurringReservationResponseDTO> list(
            Long establishmentId,
            Long sportFieldId,
            Collection<RecurringReservationListStatus> statuses,
            Boolean hasOrganizer,
            int page,
            int size
    ) {
        establishmentService.findEstablishmentById(establishmentId);

        if (sportFieldId != null) {
            sportFieldService.findSportFieldById(establishmentId, sportFieldId);
        }

        Collection<RecurringReservationListStatus> effectiveStatuses =
                statuses == null || statuses.isEmpty() ? DEFAULT_STATUSES : statuses;
        LocalDate today = LocalDate.now(clock);

        return PageResponseDTO.from(
                recurringReservationRepository.findForAdmin(
                        establishmentId,
                        sportFieldId,
                        hasOrganizer != null,
                        Boolean.TRUE.equals(hasOrganizer),
                        effectiveStatuses.contains(RecurringReservationListStatus.ACTIVE),
                        effectiveStatuses.contains(RecurringReservationListStatus.UPCOMING),
                        effectiveStatuses.contains(RecurringReservationListStatus.ENDED),
                        today,
                        PageRequests.of(page, size)
                ),
                recurring -> AdminRecurringReservationResponseDTO.fromEntity(recurring, today)
        );
    }
}
