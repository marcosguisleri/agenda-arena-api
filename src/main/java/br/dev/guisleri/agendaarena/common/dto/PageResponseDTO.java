package br.dev.guisleri.agendaarena.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Página de resultados. Formato próprio (e não o {@code Page} do Spring) para o contrato da API
 * não depender da serialização interna do Spring Data.
 */
public record PageResponseDTO<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <S, T> PageResponseDTO<T> from(Page<S> page, Function<S, T> mapper) {
        return new PageResponseDTO<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
