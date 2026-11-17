package br.dev.guisleri.agendaarena.reservation.service;

import br.dev.guisleri.agendaarena.common.dto.PageRequests;
import br.dev.guisleri.agendaarena.common.dto.PageResponseDTO;
import br.dev.guisleri.agendaarena.common.time.AdminDateRange;
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

        AdminDateRange range = AdminDateRange.resolve(from, to, LocalDate.now(clock));

        return PageResponseDTO.from(
                slotBlockRepository.findForAdmin(
                        establishmentId,
                        range.from(),
                        range.to(),
                        sportFieldId,
                        PageRequests.of(page, size)
                ),
                AdminSlotBlockResponseDTO::fromEntity
        );
    }
}
