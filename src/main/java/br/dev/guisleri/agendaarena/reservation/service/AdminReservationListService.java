package br.dev.guisleri.agendaarena.reservation.service;

import br.dev.guisleri.agendaarena.common.dto.PageRequests;
import br.dev.guisleri.agendaarena.common.dto.PageResponseDTO;
import br.dev.guisleri.agendaarena.common.exception.ValidationFailedException;
import br.dev.guisleri.agendaarena.establishment.service.EstablishmentService;
import br.dev.guisleri.agendaarena.reservation.dto.AdminReservationResponseDTO;
import br.dev.guisleri.agendaarena.reservation.model.ReservationOrigin;
import br.dev.guisleri.agendaarena.reservation.model.ReservationStatus;
import br.dev.guisleri.agendaarena.reservation.repository.ReservationRepository;
import br.dev.guisleri.agendaarena.sportfield.service.SportFieldService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumSet;

/**
 * Listagem de reservas do estabelecimento para o painel do administrador. Só leitura: não trava
 * horário nem altera nada.
 */
@Service
public class AdminReservationListService {

    public static final int DEFAULT_RANGE_DAYS = 30;
    public static final int MAX_RANGE_DAYS = 92;

    private final ReservationRepository reservationRepository;
    private final EstablishmentService establishmentService;
    private final SportFieldService sportFieldService;
    private final Clock clock;

    public AdminReservationListService(
            ReservationRepository reservationRepository,
            EstablishmentService establishmentService,
            SportFieldService sportFieldService,
            Clock clock
    ) {
        this.reservationRepository = reservationRepository;
        this.establishmentService = establishmentService;
        this.sportFieldService = sportFieldService;
        this.clock = clock;
    }

    /**
     * Sem {@code from}/{@code to}: de hoje a hoje+30. Só um dos dois: o outro fica 30 dias
     * adiante (ou atrás). Período máximo de 92 dias. Sem {@code statuses}: todos.
     */
    @Transactional(readOnly = true)
    public PageResponseDTO<AdminReservationResponseDTO> list(
            Long establishmentId,
            LocalDate from,
            LocalDate to,
            Long sportFieldId,
            Collection<ReservationStatus> statuses,
            ReservationOrigin origin,
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

        Collection<ReservationStatus> effectiveStatuses =
                statuses == null || statuses.isEmpty()
                        ? EnumSet.allOf(ReservationStatus.class)
                        : statuses;

        return PageResponseDTO.from(
                reservationRepository.findForAdmin(
                        establishmentId,
                        resolvedFrom,
                        resolvedTo,
                        effectiveStatuses,
                        sportFieldId,
                        origin,
                        PageRequests.of(page, size)
                ),
                AdminReservationResponseDTO::fromEntity
        );
    }
}
