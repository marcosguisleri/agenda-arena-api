package br.dev.guisleri.agendaarena.sportfield.service;

import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.service.EstablishmentService;
import br.dev.guisleri.agendaarena.sportfield.entity.SportField;
import br.dev.guisleri.agendaarena.sportfield.exception.SportFieldAlreadyExistsException;
import br.dev.guisleri.agendaarena.sportfield.exception.SportFieldNotFoundException;
import br.dev.guisleri.agendaarena.sportfield.model.SportType;
import br.dev.guisleri.agendaarena.sportfield.repository.SportFieldRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SportFieldService {

    private final SportFieldRepository sportFieldRepository;
    private final EstablishmentService establishmentService;

    public SportFieldService(SportFieldRepository sportFieldRepository, EstablishmentService establishmentService) {
        this.sportFieldRepository = sportFieldRepository;
        this.establishmentService = establishmentService;
    }

    public SportField createSportField(
            Long establishmentId,
            String name,
            SportType sportType
    ) {
        Establishment establishment =
                establishmentService.findEstablishmentById(establishmentId);

        if (sportFieldRepository.existsByEstablishmentIdAndName(
                establishmentId,
                name
        )) {
            throw new SportFieldAlreadyExistsException(
                    "Sport field with name '" + name
                            + "' already exists for establishment "
                            + establishmentId
            );
        }

        SportField sportField = new SportField(
                establishment,
                name,
                sportType
        );

        return sportFieldRepository.save(sportField);
    }

    public List<SportField> findAllSportFields(Long establishmentId) {
        establishmentService.findEstablishmentById(establishmentId);

        return sportFieldRepository.findAllByEstablishmentId(establishmentId);
    }

    public SportField findSportFieldById(
            Long establishmentId,
            Long sportFieldId
    ) {
        return sportFieldRepository
                .findByIdAndEstablishmentId(sportFieldId, establishmentId)
                .orElseThrow(() -> new SportFieldNotFoundException(
                        "Sport field with id " + sportFieldId
                                + " not found for establishment " + establishmentId
                ));
    }

}
