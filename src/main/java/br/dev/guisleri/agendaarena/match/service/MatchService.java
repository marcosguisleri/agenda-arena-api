package br.dev.guisleri.agendaarena.match.service;

import br.dev.guisleri.agendaarena.match.entity.Match;
import br.dev.guisleri.agendaarena.match.entity.MatchParticipant;
import br.dev.guisleri.agendaarena.match.exception.FieldPlayerLimitReachedException;
import br.dev.guisleri.agendaarena.match.exception.GoalkeeperLimitReachedException;
import br.dev.guisleri.agendaarena.match.exception.MatchNotFoundException;
import br.dev.guisleri.agendaarena.match.exception.MatchParticipantNotFoundException;
import br.dev.guisleri.agendaarena.match.exception.MatchRegistrationNotAllowedException;
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

import java.time.Clock;
import java.time.LocalDateTime;
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
            return new MatchCreationResult(withParticipants(existing.get()), false);
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
        return withParticipants(
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
        return withParticipants(
                matchRepository
                        .findByPublicToken(publicToken)
                        .orElseThrow(() -> new MatchNotFoundException("Match not found"))
        );
    }

    /**
     * Inscrição pelo link (sem conta). Trava a partida antes de validar: estado aberto, reserva
     * não cancelada, jogo ainda não começou e limite da posição.
     */
    @Transactional
    public MatchParticipant joinMatch(String publicToken, String name, PlayerPosition position) {
        Match match = matchRepository
                .findByPublicTokenForUpdate(publicToken)
                .orElseThrow(() -> new MatchNotFoundException("Match not found"));

        ensureOpen(match);
        ensureReservationAcceptsPlayers(match.getReservation());
        ensurePositionAvailable(match, position);

        return matchParticipantRepository.saveAndFlush(
                MatchParticipant.join(match, normalizeName(name), position, clock.instant())
        );
    }

    /**
     * Troca linha/goleiro (só o organizador, com a partida aberta), respeitando o limite.
     */
    @Transactional
    public MatchParticipant changeParticipantPosition(
            Long organizerId,
            Long reservationId,
            Long participantId,
            PlayerPosition position
    ) {
        Match match = findMyMatchForUpdate(organizerId, reservationId);

        ensureOpen(match);

        MatchParticipant participant = findParticipant(match, participantId);

        if (participant.getPosition() == position) {
            return participant;
        }

        ensurePositionAvailable(match, position);

        participant.changePosition(position);

        return matchParticipantRepository.saveAndFlush(participant);
    }

    @Transactional
    public void removeParticipant(Long organizerId, Long reservationId, Long participantId) {
        Match match = findMyMatchForUpdate(organizerId, reservationId);

        ensureOpen(match);

        matchParticipantRepository.delete(findParticipant(match, participantId));
    }

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

    private MatchDetails withParticipants(Match match) {
        return new MatchDetails(
                match,
                matchParticipantRepository.findAllByMatchIdOrderByCreatedAtAscIdAsc(match.getId())
        );
    }

    private static void ensureOpen(Match match) {
        if (match.getStatus() != MatchStatus.OPEN) {
            throw new MatchRegistrationNotAllowedException("Match is not open");
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
