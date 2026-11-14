package br.dev.guisleri.agendaarena.auth.repository;

import br.dev.guisleri.agendaarena.auth.entity.RefreshSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, Long> {

    /**
     * Acha a sessão pelo hash do token atual ou do anterior e trava a linha. A renovação trava
     * antes de validar: duas renovações com o mesmo token são serializadas, e a segunda enxerga o
     * token já trocado.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from RefreshSession s
            where s.currentTokenHash = :tokenHash or s.previousTokenHash = :tokenHash
            """)
    Optional<RefreshSession> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("""
            delete from RefreshSession s
            where s.user.id = :userId
              and (s.revokedAt is not null or s.expiresAt <= :now)
            """)
    int deleteFinishedByUserId(@Param("userId") Long userId, @Param("now") Instant now);
}
