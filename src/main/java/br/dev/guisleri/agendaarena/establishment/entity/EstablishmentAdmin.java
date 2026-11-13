package br.dev.guisleri.agendaarena.establishment.entity;

import br.dev.guisleri.agendaarena.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Vínculo entre um usuário administrador e um estabelecimento que ele gerencia.
 */
@Entity
@Table(name = "establishment_admins")
public class EstablishmentAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establishment_id", nullable = false)
    private Establishment establishment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EstablishmentAdmin() {
    }

    public EstablishmentAdmin(User user, Establishment establishment, Instant createdAt) {
        this.user = user;
        this.establishment = establishment;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Establishment getEstablishment() {
        return establishment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

}
