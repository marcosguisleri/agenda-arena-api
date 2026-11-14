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

    /**
     * E-mails dos administradores ativos do estabelecimento (para os avisos de reserva).
     */
    @Query("""
            select distinct link.user.email
            from EstablishmentAdmin link
            where link.establishment.id = :establishmentId
              and link.user.active = true
            """)
    List<String> findActiveAdminEmails(@Param("establishmentId") Long establishmentId);

    @Query("""
            select link.establishment
            from EstablishmentAdmin link
            where link.user.id = :userId
            order by link.establishment.name, link.establishment.id
            """)
    List<Establishment> findEstablishmentsByUserId(@Param("userId") Long userId);

}
