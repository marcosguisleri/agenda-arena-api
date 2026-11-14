package br.dev.guisleri.agendaarena.match.service;

import br.dev.guisleri.agendaarena.match.model.PixSource;
import br.dev.guisleri.agendaarena.pix.model.PixKeyType;

/**
 * Escolha de chave Pix feita pelo organizador. Os campos da chave só valem para
 * {@link PixSource#ORGANIZER}.
 */
public record PixChoice(
        PixSource source,
        PixKeyType keyType,
        String key,
        String holderName,
        String bankName
) {

    public boolean hasKeyFields() {
        return keyType != null
                || (key != null && !key.isBlank())
                || (holderName != null && !holderName.isBlank())
                || (bankName != null && !bankName.isBlank());
    }
}
