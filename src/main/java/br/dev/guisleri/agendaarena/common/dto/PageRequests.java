package br.dev.guisleri.agendaarena.common.dto;

import br.dev.guisleri.agendaarena.common.exception.ValidationFailedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Valida {@code page}/{@code size} vindos da URL. A ordem dos resultados é fixa em cada consulta
 * (não vem do cliente), então a página não leva ordenação.
 */
public final class PageRequests {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private PageRequests() {
    }

    public static Pageable of(int page, int size) {
        if (page < 0) {
            throw new ValidationFailedException("page", "Page must be 0 or greater");
        }

        if (size < 1 || size > MAX_SIZE) {
            throw new ValidationFailedException(
                    "size",
                    "Size must be between 1 and " + MAX_SIZE
            );
        }

        return PageRequest.of(page, size);
    }
}
