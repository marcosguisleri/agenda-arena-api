package br.dev.guisleri.agendaarena.establishment.service;

import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.exception.EstablishmentNotFoundException;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EstablishmentService {

    private final EstablishmentRepository establishmentRepository;

    public EstablishmentService(
            EstablishmentRepository establishmentRepository
    ) {
        this.establishmentRepository = establishmentRepository;
    }

    public Establishment createEstablishment(String name) {

        Establishment establishment = new Establishment(name);

        return establishmentRepository.save(establishment);
    }

    public List<Establishment> findAllEstablishments() {
        return establishmentRepository.findAll();
    }

    public Establishment findEstablishmentById(Long establishmentId) {
        return establishmentRepository.findById(establishmentId)
                .orElseThrow(() -> new EstablishmentNotFoundException(
                                "Establishment with id " + establishmentId + " not found"
                        )
                );
    }
}
