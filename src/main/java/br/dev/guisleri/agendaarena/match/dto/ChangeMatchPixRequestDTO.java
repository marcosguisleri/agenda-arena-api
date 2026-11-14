package br.dev.guisleri.agendaarena.match.dto;

import br.dev.guisleri.agendaarena.match.model.PixSource;
import br.dev.guisleri.agendaarena.match.service.PixChoice;
import br.dev.guisleri.agendaarena.pix.model.PixKeyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/**
 * Corpo de {@code PUT /me/matches/{id}/pix-key}: a mesma escolha da criação ({@code source} e,
 * para {@code ORGANIZER}, os dados da chave).
 * <p>
 * Compatibilidade temporária com o formato antigo {@code {"pixKey": "texto"}}: {@code null} ou
 * vazio continua removendo a chave; texto preenchido é recusado com uma mensagem que explica o
 * formato novo (sem tipo e titular não há como montar uma chave válida). Remover quando o front
 * migrar.
 */
public record ChangeMatchPixRequestDTO(

        PixSource source,

        PixKeyType keyType,

        @Size(max = 140, message = "Key must have at most 140 characters")
        String key,

        @Size(max = 120, message = "Holder name must have at most 120 characters")
        String holderName,

        @Size(max = 80, message = "Bank name must have at most 80 characters")
        String bankName,

        @Schema(
                deprecated = true,
                description = "Obsoleto. Só {\"pixKey\": null} ou vazio (remover a chave) ainda "
                        + "é aceito; use DELETE ou o formato com source."
        )
        JsonNode pixKey

) {

    /**
     * A propriedade antiga {@code pixKey} veio no corpo (mesmo que com valor nulo).
     */
    public boolean hasLegacyPixKey() {
        return pixKey != null;
    }

    /**
     * No formato antigo, nulo ou texto vazio significava "remover a chave".
     */
    public boolean legacyPixKeyMeansRemoval() {
        return pixKey.isNull() || (pixKey.isString() && pixKey.asString().isBlank());
    }

    public PixChoice toChoice() {
        return new PixChoice(source, keyType, key, holderName, bankName);
    }
}
