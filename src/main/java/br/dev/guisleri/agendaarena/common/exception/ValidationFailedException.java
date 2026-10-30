package br.dev.guisleri.agendaarena.common.exception;

/**
 * Falha de validação detectada fora do Bean Validation (ex.: regra que depende de quem chama).
 * Vira 400 no mesmo formato dos erros de validação, com o campo em {@code errors}.
 */
public class ValidationFailedException extends RuntimeException {

    private final String field;

    public ValidationFailedException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
