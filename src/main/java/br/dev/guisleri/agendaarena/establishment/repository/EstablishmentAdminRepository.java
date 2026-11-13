package br.dev.guisleri.agendaarena.establishment.repository;

import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.entity.EstablishmentAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EstablishmentAdminRepository
        extends JpaRepository<EstablishmentAdmin, Long> {

    boolean existsByUserIdAndEstablishmentId(Long userId, Long establishmentId);

    @Query("""
            select link.establishment
            from EstablishmentAdmin link
            where link.user.id = :userId
            order by link.establishment.name, link.establishment.id
            """)
    List<Establishment> findEstablishmentsByUserId(@Param("userId") Long userId);

}
