package br.dev.guisleri.agendaarena.establishment.repository;

import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EstablishmentRepository
        extends JpaRepository<Establishment, Long> {
}
