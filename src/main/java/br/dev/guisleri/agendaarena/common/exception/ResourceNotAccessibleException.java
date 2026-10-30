package br.dev.guisleri.agendaarena.common.exception;

/**
 * Usada quando o usuário autenticado tenta acessar dados de outro estabelecimento.
 * Vira 404 (e não 403) para não revelar que o recurso existe.
 */
public class ResourceNotAccessibleException extends RuntimeException {
    public ResourceNotAccessibleException(String message) {
        super(message);
    }
}
