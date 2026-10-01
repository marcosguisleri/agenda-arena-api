package br.dev.guisleri.agendaarena.sportfield.entity;

import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.sportfield.model.SportType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "sports_fields")
public class SportField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establishment_id", nullable = false)
    private Establishment establishment;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "sport_type", nullable = false, length = 50)
    private SportType sportType;

    @Column(nullable = false)
    private boolean active;

    protected SportField() {
    }

    public SportField(
            Establishment establishment,
            String name,
            SportType sportType
    ) {
        this.establishment = establishment;
        this.name = name;
        this.sportType = sportType;
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public Establishment getEstablishment() {
        return establishment;
    }

    public String getName() {
        return name;
    }

    public SportType getSportType() {
        return sportType;
    }

    public boolean isActive() {
        return active;
    }
}
