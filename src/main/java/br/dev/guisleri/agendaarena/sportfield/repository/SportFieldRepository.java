package br.dev.guisleri.agendaarena.sportfield.repository;

import br.dev.guisleri.agendaarena.sportfield.entity.SportField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SportFieldRepository extends JpaRepository<SportField, Long> {

    List<SportField> findAllByEstablishmentId(Long establishmentId);

    Optional<SportField> findByIdAndEstablishmentId(
            Long sportFieldId,
            Long establishmentId
    );

    boolean existsByEstablishmentIdAndName(
            Long establishmentId,
            String name
    );

}
