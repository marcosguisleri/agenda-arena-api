package br.dev.guisleri.agendaarena.establishment.exception;

/**
 * O organizador pediu a chave Pix do estabelecimento, mas ele não configurou nenhuma.
 */
public class EstablishmentPixKeyNotConfiguredException extends RuntimeException {
    public EstablishmentPixKeyNotConfiguredException(String message) {
        super(message);
    }
}
