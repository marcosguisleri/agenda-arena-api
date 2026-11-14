package br.dev.guisleri.agendaarena.pix.dto;

import br.dev.guisleri.agendaarena.pix.entity.PixKey;
import br.dev.guisleri.agendaarena.pix.model.PixKeyType;

public record PixKeyResponseDTO(
        PixKeyType keyType,
        String key,
        String holderName,
        String bankName
) {

    public static PixKeyResponseDTO fromEntity(PixKey pixKey) {
        return new PixKeyResponseDTO(
                pixKey.getKeyType(),
                pixKey.getKeyValue(),
                pixKey.getHolderName(),
                pixKey.getBankName()
        );
    }
}
