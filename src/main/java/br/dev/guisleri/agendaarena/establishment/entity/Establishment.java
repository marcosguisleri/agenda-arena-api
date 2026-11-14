package br.dev.guisleri.agendaarena.establishment.entity;

import br.dev.guisleri.agendaarena.pix.entity.PixKey;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "establishments")
public class Establishment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private boolean active;

    /**
     * Chave Pix do estabelecimento; o organizador pode escolhê-la ao criar a partida.
     */
    @Embedded
    private PixKey pix;

    protected Establishment() {
    }

    public Establishment(String name) {
        this.name = name;
        this.active = true;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public void changePix(PixKey pix) {
        this.pix = pix;
    }

    public void removePix() {
        this.pix = null;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public PixKey getPix() {
        return pix;
    }

}
