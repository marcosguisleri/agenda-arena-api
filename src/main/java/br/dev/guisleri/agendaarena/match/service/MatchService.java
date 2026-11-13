package br.dev.guisleri.agendaarena.match.service;

import br.dev.guisleri.agendaarena.match.entity.Match;
import br.dev.guisleri.agendaarena.match.entity.MatchParticipant;
import br.dev.guisleri.agendaarena.match.exception.FieldPlayerLimitReachedException;
import br.dev.guisleri.agendaarena.match.exception.GoalkeeperLimitReachedException;
import br.dev.guisleri.agendaarena.match.exception.MatchInvalidStateException;
import br.dev.guisleri.agendaarena.match.exception.MatchNotFoundException;
import br.dev.guisleri.agendaarena.match.exception.MatchParticipantNotFoundException;
import br.dev.guisleri.agendaarena.match.exception.MatchRegistrationNotAllowedException;
import br.dev.guisleri.agendaarena.match.exception.ParticipantPaymentConflictException;
import br.dev.guisleri.agendaarena.match.exception.ReservationNotEligibleForMatchException;
import br.dev.guisleri.agendaarena.match.model.MatchStatus;
import br.dev.guisleri.agendaarena.match.model.PlayerPosition;
import br.dev.guisleri.agendaarena.match.repository.MatchParticipantRepository;
import br.dev.guisleri.agendaarena.match.repository.MatchRepository;
import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.reservation.model.ReservationStatus;
import br.dev.guisleri.agendaarena.reservation.service.ReservationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MatchService {

    /**
     * Goleiros são opcionais, até 4 (escopo MVP v2.0).
     */
    public static final int MAX_GOALKEEPERS = 4;

    /**
     * Não há limite de jogo para jogadores de linha; este teto só barra abuso do link público.
     */
    public static final int MAX_FIELD_PLAYERS = 60;

    private static final BigDecimal ZERO_SHARE = BigDecimal.valueOf(0, 2);

    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final ReservationService reservationService;
    private final MatchTokenGenerator matchTokenGenerator;
    private final Clock clock;

    public MatchService(
            MatchRepository matchRepository,
            MatchParticipantRepository matchParticipantRepository,
            ReservationService reservationService,
            MatchTokenGenerator matchTokenGenerator,
            Clock clock
    ) {
        this.matchRepository = matchRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.reservationService = reservationService;
        this.matchTokenGenerator = matchTokenGenerator;
        this.clock = clock;
    }

    // ---------- Criação e leitura ----------

    /**
     * Cria a partida da reserva do organizador, ou devolve a existente (idempotente).
     * <p>
     * Trava o horário da reserva antes de ler o estado dela e de procurar a partida: assim duas
     * criações simultâneas se serializam (a segunda encontra a partida da primeira) e um
     * cancelamento do administrador não passa no meio da validação.
     */
    @Transactional
    public MatchCreationResult createMatch(Long organizerId, Long reservationId) {
        Reservation reservation =
                reservationService.findOrganizerReservationForUpdate(organizerId, reservationId);

        Optional<Match> existing =
                matchRepository.findByReservationIdAndReservationOrganizerId(
                        reservationId,
                        organizerId
                );

        if (existing.isPresent()) {
            return new MatchCreationResult(details(existing.get()), false);
        }

        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new ReservationNotEligibleForMatchException(
                    "Only confirmed reservations can have a match"
            );
        }

        matchRepository.saveAndFlush(
                Match.open(reservation, matchTokenGenerator.newToken(), clock.instant())
        );

        // Relê com o grafo completo (campo e estabelecimento) para o DTO fora da transação.
        return new MatchCreationResult(findMyMatch(organizerId, reservationId), true);
    }

    /**
     * Partida de reserva de outro organizador é tratada como inexistente (404).
     */
    @Transactional(readOnly = true)
    public MatchDetails findMyMatch(Long organizerId, Long reservationId) {
        return details(
                matchRepository
                        .findByReservationIdAndReservationOrganizerId(reservationId, organizerId)
                        .orElseThrow(() -> matchNotFound(reservationId))
        );
    }

    /**
     * Acesso pelo link público. A mensagem não repete o token recebido.
     */
    @Transactional(readOnly = true)
    public MatchDetails findPublicMatch(String publicToken) {
        return details(
                matchRepository
                        .findByPublicToken(publicToken)
                        .orElseThrow(() -> new MatchNotFoundException("Match not found"))
        );
    }

    // ---------- Lista de participantes ----------

    /**
     * Inscrição pelo link (sem conta). Trava a partida antes de validar: estado aberto, reserva
     * não cancelada, jogo ainda não começou e limite da posição. Devolve a página atualizada,
     * porque a cota de todos muda.
     */
    @Transactional
    public MatchDetails joinMatch(String publicToken, String name, PlayerPosition position) {
        Match match = matchRepository
                .findByPublicTokenForUpdate(publicToken)
                .orElseThrow(() -> new MatchNotFoundException("Match not found"));

        ensureOpen(match);
        ensureReservationAcceptsPlayers(match.getReservation());
        ensurePositionAvailable(match, position);

        matchParticipantRepository.saveAndFlush(
                MatchParticipant.join(match, normalizeName(name), position, clock.instant())
        );

        return findPublicMatch(publicToken);
    }

    /**
     * Troca linha/goleiro (só o organizador, com a partida aberta), respeitando o limite. A
     * isenção do rateio volta ao padrão da nova posição, o que muda a cota de todos.
     */
    @Transactional
    public MatchDetails changeParticipantPosition(
            Long organizerId,
            Long reservationId,
            Long participantId,
            PlayerPosition position
    ) {
        Match match = findMyMatchForUpdate(organizerId, reservationId);

        ensureOpen(match);

        MatchParticipant participant = findParticipant(match, participantId);

        if (participant.getPosition() != position) {
            ensurePositionAvailable(match, position);
            ensurePaymentAllowsExemption(
                    participant,
                    MatchParticipant.paysShareByDefault(position)
            );

            participant.changePosition(position);
            matchParticipantRepository.saveAndFlush(participant);
        }

        return findMyMatch(organizerId, reservationId);
    }

    @Transactional
    public void removeParticipant(Long organizerId, Long reservationId, Long participantId) {
        Match match = findMyMatchForUpdate(organizerId, reservationId);

        ensureOpen(match);

        matchParticipantRepository.delete(findParticipant(match, participantId));
    }

    // ---------- Rateio e pagamentos ----------

    /**
     * Inclui ou isenta um participante do rateio (só com a partida aberta).
     */
    @Transactional
    public MatchDetails changeParticipantPaysShare(
            Long organizerId,
            Long reservationId,
            Long participantId,
            boolean paysShare
    ) {
        Match match = findMyMatchForUpdate(organizerId, reservationId);

        ensureOpen(match);

        MatchParticipant participant = findParticipant(match, participantId);

        ensurePaymentAllowsExemption(participant, paysShare);

        participant.changePaysShare(paysShare);
        matchParticipantRepository.saveAndFlush(participant);

        return findMyMatch(organizerId, reservationId);
    }

    /**
     * Declara (ou desfaz) o pagamento de um participante, com a partida aberta ou fechada.
     * Guarda a cota do momento, para sinalizar se ela mudar depois (reabertura).
     */
    @Transactional
    public MatchDetails changeParticipantPayment(
            Long organizerId,
            Long reservationId,
            Long participantId,
            boolean paid
    ) {
        Match match = findMyMatchForUpdate(organizerId, reservationId);

        ensureNotFinished(match);

        MatchParticipant participant = findParticipant(match, participantId);

        if (paid) {
            if (!participant.isPaysShare()) {
                throw new ParticipantPaymentConflictException(
                        "Participant is exempt from the split"
                );
            }

            participant.markPaid(details(match).shareOf(participant));
        } else {
            participant.unmarkPaid();
        }

        matchParticipantRepository.saveAndFlush(participant);

        return findMyMatch(organizerId, reservationId);
    }

    /**
     * Define a chave Pix exibida na página (vazia remove). Só exibição: o pagamento é externo.
     */
    @Transactional
    public MatchDetails changePixKey(Long organizerId, Long reservationId, String pixKey) {
        Match match = findMyMatchForUpdate(organizerId, reservationId);

        ensureNotFinished(match);

        match.changePixKey(pixKey == null || pixKey.isBlank() ? null : pixKey.trim());
        matchRepository.saveAndFlush(match);

        return findMyMatch(organizerId, reservationId);
    }

    // ---------- Fechar e reabrir ----------

    /**
     * Fecha a partida: congela a lista e as cotas e bloqueia inscrições. Sob o lock da partida,
     * nenhuma inscrição concorrente entra depois do cálculo.
     */
    @Transactional
    public MatchDetails closeMatch(Long organizerId, Long reservationId) {
        Match match = findMyMatchForUpdate(organizerId, reservationId);

        if (match.getStatus() != MatchStatus.OPEN) {
            throw new MatchInvalidStateException("Match is not open");
        }

        MatchDetails current = details(match);

        if (current.participants().stream().noneMatch(MatchParticipant::isPaysShare)) {
            throw new MatchInvalidStateException("Match has no paying participants");
        }

        for (MatchParticipant participant : current.participants()) {
            participant.freezeShare(current.shareOf(participant));
        }

        match.close(clock.instant());

        matchParticipantRepository.saveAll(current.participants());
        matchRepository.saveAndFlush(match);

        return findMyMatch(organizerId, reservationId);
    }

    /**
     * Reabre: as cotas voltam a ser calculadas na hora. Pagamentos declarados são mantidos; se a
     * cota mudar, a visão do organizador sinaliza (decisão pendente nº 7, recomendação aceita).
     */
    @Transactional
    public MatchDetails reopenMatch(Long organizerId, Long reservationId) {
        Match match = findMyMatchForUpdate(organizerId, reservationId);

        if (match.getStatus() != MatchStatus.CLOSED) {
            throw new MatchInvalidStateException("Match is not closed");
        }

        List<MatchParticipant> participants =
                matchParticipantRepository.findAllByMatchIdOrderByCreatedAtAscIdAsc(match.getId());

        participants.forEach(MatchParticipant::unfreezeShare);
        match.reopen();

        matchParticipantRepository.saveAll(participants);
        matchRepository.saveAndFlush(match);

        return findMyMatch(organizerId, reservationId);
    }

    // ---------- Apoio ----------

    private Match findMyMatchForUpdate(Long organizerId, Long reservationId) {
        Long matchId = matchRepository
                .findIdByReservationIdAndOrganizerId(reservationId, organizerId)
                .orElseThrow(() -> matchNotFound(reservationId));

        return matchRepository
                .findByIdForUpdate(matchId)
                .orElseThrow(() -> matchNotFound(reservationId));
    }

    private MatchParticipant findParticipant(Match match, Long participantId) {
        return matchParticipantRepository
                .findByIdAndMatchId(participantId, match.getId())
                .orElseThrow(() -> new MatchParticipantNotFoundException(
                        "Participant with id " + participantId + " not found"
                ));
    }

    /**
     * Participantes em ordem de inscrição e a cota de cada um: congelada (fechada) ou calculada
     * agora (aberta). Isentos têm cota zero.
     */
    private MatchDetails details(Match match) {
        List<MatchParticipant> participants =
                matchParticipantRepository.findAllByMatchIdOrderByCreatedAtAscIdAsc(match.getId());
        Map<Long, BigDecimal> shares = new HashMap<>();

        if (match.getStatus() == MatchStatus.OPEN) {
            List<MatchParticipant> payers = participants.stream()
                    .filter(MatchParticipant::isPaysShare)
                    .toList();
            Iterator<BigDecimal> payerShares = MatchSplitCalculator
                    .split(match.getReservation().getPrice(), payers.size())
                    .iterator();

            for (MatchParticipant participant : participants) {
                shares.put(
                        participant.getId(),
                        participant.isPaysShare() ? payerShares.next() : ZERO_SHARE
                );
            }
        } else {
            for (MatchParticipant participant : participants) {
                shares.put(
                        participant.getId(),
                        participant.getShareAmount() != null
                                ? participant.getShareAmount()
                                : ZERO_SHARE
                );
            }
        }

        return new MatchDetails(match, participants, shares);
    }

    private static void ensureOpen(Match match) {
        if (match.getStatus() != MatchStatus.OPEN) {
            throw new MatchRegistrationNotAllowedException("Match is not open");
        }
    }

    private static void ensureNotFinished(Match match) {
        if (match.getStatus() == MatchStatus.FINISHED) {
            throw new MatchInvalidStateException("Match is finished");
        }
    }

    /**
     * Isentar quem já declarou pagamento apagaria a declaração em silêncio: o organizador precisa
     * desmarcar o pagamento antes.
     */
    private static void ensurePaymentAllowsExemption(
            MatchParticipant participant,
            boolean paysShare
    ) {
        if (!paysShare && participant.isPaid()) {
            throw new ParticipantPaymentConflictException(
                    "Participant has a declared payment"
            );
        }
    }

    private void ensureReservationAcceptsPlayers(Reservation reservation) {
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new MatchRegistrationNotAllowedException("Reservation was cancelled");
        }

        LocalDateTime startsAt = LocalDateTime.of(
                reservation.getReservationDate(),
                reservation.getStartTime()
        );

        if (!startsAt.isAfter(LocalDateTime.now(clock))) {
            throw new MatchRegistrationNotAllowedException("Match has already started");
        }
    }

    /**
     * Chamado com a partida travada: a contagem não muda até o fim da transação.
     */
    private void ensurePositionAvailable(Match match, PlayerPosition position) {
        long count =
                matchParticipantRepository.countByMatchIdAndPosition(match.getId(), position);

        if (position == PlayerPosition.GOALKEEPER && count >= MAX_GOALKEEPERS) {
            throw new GoalkeeperLimitReachedException(
                    "Goalkeeper limit of " + MAX_GOALKEEPERS + " reached"
            );
        }

        if (position == PlayerPosition.FIELD && count >= MAX_FIELD_PLAYERS) {
            throw new FieldPlayerLimitReachedException(
                    "Field player limit of " + MAX_FIELD_PLAYERS + " reached"
            );
        }
    }

    private static String normalizeName(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }

    private static MatchNotFoundException matchNotFound(Long reservationId) {
        return new MatchNotFoundException(
                "Match for reservation with id " + reservationId + " not found"
        );
    }
}
