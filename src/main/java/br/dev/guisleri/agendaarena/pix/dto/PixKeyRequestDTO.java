package br.dev.guisleri.agendaarena.pix.dto;

import br.dev.guisleri.agendaarena.pix.model.PixKeyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Chave Pix completa. O valor da chave é validado conforme {@code keyType} no servidor.
 *
 * @param holderName nome completo de quem recebe
 * @param bankName   opcional
 */
public record PixKeyRequestDTO(

        @NotNull(message = "Key type is required")
        PixKeyType keyType,

        @NotBlank(message = "Key is required")
        @Size(max = 140, message = "Key must have at most 140 characters")
        String key,

        @NotBlank(message = "Holder name is required")
        @Size(max = 120, message = "Holder name must have at most 120 characters")
        String holderName,

        @Size(max = 80, message = "Bank name must have at most 80 characters")
        String bankName

) {
}
