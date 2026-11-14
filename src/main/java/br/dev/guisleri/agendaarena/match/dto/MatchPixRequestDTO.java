package br.dev.guisleri.agendaarena.match.dto;

import br.dev.guisleri.agendaarena.match.model.PixSource;
import br.dev.guisleri.agendaarena.match.service.PixChoice;
import br.dev.guisleri.agendaarena.pix.model.PixKeyType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Escolha da chave Pix da partida. Com {@code source = ESTABLISHMENT} usa a chave configurada
 * pelo estabelecimento e os demais campos não são aceitos. Com {@code source = ORGANIZER}, o
 * organizador informa tipo, chave, nome completo do titular e (opcional) o banco.
 */
public record MatchPixRequestDTO(

        @NotNull(message = "Source is required")
        PixSource source,

        PixKeyType keyType,

        @Size(max = 140, message = "Key must have at most 140 characters")
        String key,

        @Size(max = 120, message = "Holder name must have at most 120 characters")
        String holderName,

        @Size(max = 80, message = "Bank name must have at most 80 characters")
        String bankName

) {

    public PixChoice toChoice() {
        return new PixChoice(source, keyType, key, holderName, bankName);
    }
}
