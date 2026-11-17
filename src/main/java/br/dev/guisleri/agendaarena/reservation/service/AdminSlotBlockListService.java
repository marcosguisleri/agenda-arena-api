package br.dev.guisleri.agendaarena.reservation.service;

import br.dev.guisleri.agendaarena.common.dto.PageRequests;
import br.dev.guisleri.agendaarena.common.dto.PageResponseDTO;
import br.dev.guisleri.agendaarena.common.exception.ValidationFailedException;
import br.dev.guisleri.agendaarena.establishment.service.EstablishmentService;
import br.dev.guisleri.agendaarena.reservation.dto.AdminSlotBlockResponseDTO;
import br.dev.guisleri.agendaarena.reservation.repository.SlotBlockRepository;
import br.dev.guisleri.agendaarena.sportfield.service.SportFieldService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Listagem de bloqueios do estabelecimento para o painel do administrador. Só leitura: não trava
 * horário nem altera nada. Mesma janela de datas das demais listagens do admin.
 */
@Service
public class AdminSlotBlockListService {

    private final SlotBlockRepository slotBlockRepository;
    private final EstablishmentService establishmentService;
    private final SportFieldService sportFieldService;
    private final Clock clock;

    public AdminSlotBlockListService(
            SlotBlockRepository slotBlockRepository,
            EstablishmentService establishmentService,
            SportFieldService sportFieldService,
            Clock clock
    ) {
        this.slotBlockRepository = slotBlockRepository;
        this.establishmentService = establishmentService;
        this.sportFieldService = sportFieldService;
        this.clock = clock;
    }

    /**
     * Sem {@code from}/{@code to}: de hoje a hoje+30. Só um dos dois: o outro fica 30 dias
     * adiante (ou atrás). Período máximo de 92 dias.
     */
    @Transactional(readOnly = true)
    public PageResponseDTO<AdminSlotBlockResponseDTO> list(
            Long establishmentId,
            LocalDate from,
            LocalDate to,
            Long sportFieldId,
            int page,
            int size
    ) {
        establishmentService.findEstablishmentById(establishmentId);

        if (sportFieldId != null) {
            sportFieldService.findSportFieldById(establishmentId, sportFieldId);
        }

        int defaultRangeDays = AdminReservationListService.DEFAULT_RANGE_DAYS;
        int maxRangeDays = AdminReservationListService.MAX_RANGE_DAYS;

        LocalDate resolvedFrom = from != null
                ? from
                : to != null ? to.minusDays(defaultRangeDays) : LocalDate.now(clock);
        LocalDate resolvedTo = to != null
                ? to
                : resolvedFrom.plusDays(defaultRangeDays);

        if (resolvedTo.isBefore(resolvedFrom)) {
            throw new ValidationFailedException("to", "Date 'to' cannot be before 'from'");
        }

        if (resolvedTo.isAfter(resolvedFrom.plusDays(maxRangeDays - 1L))) {
            throw new ValidationFailedException(
                    "to",
                    "Period cannot exceed " + maxRangeDays + " days"
            );
        }

        return PageResponseDTO.from(
                slotBlockRepository.findForAdmin(
                        establishmentId,
                        resolvedFrom,
                        resolvedTo,
                        sportFieldId,
                        PageRequests.of(page, size)
                ),
                AdminSlotBlockResponseDTO::fromEntity
        );
    }
}
