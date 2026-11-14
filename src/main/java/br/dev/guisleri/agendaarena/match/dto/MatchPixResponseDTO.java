package br.dev.guisleri.agendaarena.match.dto;

import br.dev.guisleri.agendaarena.match.entity.Match;
import br.dev.guisleri.agendaarena.match.model.PixSource;
import br.dev.guisleri.agendaarena.pix.model.PixKeyType;

/**
 * Chave Pix exibida na partida; o campo inteiro é nulo quando a partida não tem Pix.
 */
public record MatchPixResponseDTO(
        PixSource source,
        PixKeyType keyType,
        String key,
        String holderName,
        String bankName
) {

    public static MatchPixResponseDTO fromMatch(Match match) {
        if (match.getPix() == null) {
            return null;
        }

        return new MatchPixResponseDTO(
                match.getPixSource(),
                match.getPix().getKeyType(),
                match.getPix().getKeyValue(),
                match.getPix().getHolderName(),
                match.getPix().getBankName()
        );
    }
}
