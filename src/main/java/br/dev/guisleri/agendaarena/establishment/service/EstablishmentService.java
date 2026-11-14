package br.dev.guisleri.agendaarena.establishment.service;

import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.exception.EstablishmentNotFoundException;
import br.dev.guisleri.agendaarena.establishment.exception.EstablishmentPixKeyNotFoundException;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentRepository;
import br.dev.guisleri.agendaarena.pix.entity.PixKey;
import br.dev.guisleri.agendaarena.pix.model.PixKeyType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EstablishmentService {

    private final EstablishmentRepository establishmentRepository;

    public EstablishmentService(
            EstablishmentRepository establishmentRepository
    ) {
        this.establishmentRepository = establishmentRepository;
    }

    public Establishment createEstablishment(String name) {

        Establishment establishment = new Establishment(name);

        return establishmentRepository.save(establishment);
    }

    public List<Establishment> findAllEstablishments() {
        return establishmentRepository.findAll();
    }

    public Establishment findEstablishmentById(Long establishmentId) {
        return establishmentRepository.findById(establishmentId)
                .orElseThrow(() -> new EstablishmentNotFoundException(
                                "Establishment with id " + establishmentId + " not found"
                        )
                );
    }

    public void updateEstablishmentStatus(
            Long establishmentId,
            boolean active
    ) {
        Establishment establishment = findEstablishmentById(establishmentId);

        if (active) {
            establishment.activate();
        } else {
            establishment.deactivate();
        }

        establishmentRepository.save(establishment);
    }

    /**
     * Define ou troca a chave Pix do estabelecimento (validada e normalizada por {@link PixKey}).
     * Partidas que já copiaram a chave antiga não mudam sozinhas: o organizador reescolhe.
     */
    @Transactional
    public PixKey changePixKey(
            Long establishmentId,
            PixKeyType keyType,
            String key,
            String holderName,
            String bankName
    ) {
        Establishment establishment = findEstablishmentById(establishmentId);
        PixKey pixKey = PixKey.of(keyType, key, holderName, bankName);

        establishment.changePix(pixKey);
        establishmentRepository.saveAndFlush(establishment);

        return pixKey;
    }

    @Transactional(readOnly = true)
    public PixKey findPixKey(Long establishmentId) {
        PixKey pixKey = findEstablishmentById(establishmentId).getPix();

        if (pixKey == null) {
            throw new EstablishmentPixKeyNotFoundException(
                    "Establishment has no Pix key configured"
            );
        }

        return pixKey;
    }

    /**
     * Remove a chave (idempotente: sem chave, nada acontece).
     */
    @Transactional
    public void removePixKey(Long establishmentId) {
        Establishment establishment = findEstablishmentById(establishmentId);

        establishment.removePix();
        establishmentRepository.saveAndFlush(establishment);
    }
}
