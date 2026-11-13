package br.dev.guisleri.agendaarena.establishment.service;

import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.entity.EstablishmentAdmin;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentAdminRepository;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentRepository;
import br.dev.guisleri.agendaarena.establishment.exception.EstablishmentNotFoundException;
import br.dev.guisleri.agendaarena.user.entity.User;
import br.dev.guisleri.agendaarena.user.model.UserRole;
import br.dev.guisleri.agendaarena.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class EstablishmentAdminService {

    private final EstablishmentAdminRepository establishmentAdminRepository;
    private final EstablishmentRepository establishmentRepository;
    private final UserService userService;
    private final Clock clock;

    public EstablishmentAdminService(
            EstablishmentAdminRepository establishmentAdminRepository,
            EstablishmentRepository establishmentRepository,
            UserService userService,
            Clock clock
    ) {
        this.establishmentAdminRepository = establishmentAdminRepository;
        this.establishmentRepository = establishmentRepository;
        this.userService = userService;
        this.clock = clock;
    }

    /**
     * Cria o usuário administrador e o vincula ao estabelecimento na mesma transação: se o
     * vínculo falhar, o usuário também não é criado.
     */
    @Transactional
    public User createAdmin(
            Long establishmentId,
            String name,
            String email,
            String phone,
            String password
    ) {
        Establishment establishment = establishmentRepository.findById(establishmentId)
                .orElseThrow(() -> new EstablishmentNotFoundException(
                        "Establishment with id " + establishmentId + " not found"
                ));

        User user = userService.createUser(
                name,
                email,
                phone,
                password,
                UserRole.ESTABLISHMENT_ADMIN
        );

        establishmentAdminRepository.save(
                new EstablishmentAdmin(user, establishment, Instant.now(clock))
        );

        return user;
    }

    @Transactional(readOnly = true)
    public List<Establishment> findManagedEstablishments(Long userId) {
        return establishmentAdminRepository.findEstablishmentsByUserId(userId);
    }

}
