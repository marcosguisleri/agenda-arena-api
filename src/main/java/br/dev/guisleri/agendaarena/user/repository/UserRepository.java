package br.dev.guisleri.agendaarena.user.repository;

import br.dev.guisleri.agendaarena.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

}
