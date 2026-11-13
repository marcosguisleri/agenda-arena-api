package br.dev.guisleri.agendaarena.match.service;

import br.dev.guisleri.agendaarena.match.entity.Match;
import br.dev.guisleri.agendaarena.match.exception.MatchNotFoundException;
import br.dev.guisleri.agendaarena.match.exception.ReservationNotEligibleForMatchException;
import br.dev.guisleri.agendaarena.match.repository.MatchRepository;
import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.reservation.model.ReservationStatus;
import br.dev.guisleri.agendaarena.reservation.service.ReservationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

@Service
public class MatchService {

    private final MatchRepository matchRepository;
    private final ReservationService reservationService;
    private final MatchTokenGenerator matchTokenGenerator;
    private final Clock clock;

    public MatchService(
            MatchRepository matchRepository,
            ReservationService reservationService,
            MatchTokenGenerator matchTokenGenerator,
            Clock clock
    ) {
        this.matchRepository = matchRepository;
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
            return new MatchCreationResult(existing.get(), false);
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
    public Match findMyMatch(Long organizerId, Long reservationId) {
        return matchRepository
                .findByReservationIdAndReservationOrganizerId(reservationId, organizerId)
                .orElseThrow(() -> new MatchNotFoundException(
                        "Match for reservation with id " + reservationId + " not found"
                ));
    }

    /**
     * Acesso pelo link público. A mensagem não repete o token recebido.
     */
    @Transactional(readOnly = true)
    public Match findPublicMatch(String publicToken) {
        return matchRepository
                .findByPublicToken(publicToken)
                .orElseThrow(() -> new MatchNotFoundException("Match not found"));
    }
}
