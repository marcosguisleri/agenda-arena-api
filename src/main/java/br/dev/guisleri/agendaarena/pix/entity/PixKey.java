package br.dev.guisleri.agendaarena.pix.entity;

import br.dev.guisleri.agendaarena.common.exception.ValidationFailedException;
import br.dev.guisleri.agendaarena.pix.model.PixKeyType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Chave Pix de quem recebe: tipo, valor normalizado, nome completo do titular e banco (opcional).
 * Só é exibida; o pagamento acontece fora da plataforma. Sempre criada por {@link #of}, que valida
 * o valor conforme o tipo (inclusive os dígitos verificadores de CPF e CNPJ) e devolve um objeto
 * já normalizado. Cada dono (estabelecimento, partida) guarda a sua própria instância: use
 * {@link #copy()} para copiar de um para outro.
 */
@Embeddable
public class PixKey {

    /** Limite do valor da chave no Pix (e-mail); o campo guarda mais por causa de máscaras. */
    private static final int EMAIL_MAX_LENGTH = 77;
    private static final int HOLDER_NAME_MAX_LENGTH = 120;
    private static final int BANK_NAME_MAX_LENGTH = 80;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Pattern CPF_PATTERN = Pattern.compile("^\\d{11}$");
    private static final Pattern CNPJ_PATTERN = Pattern.compile("^[A-Z0-9]{12}\\d{2}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+55[1-9]\\d[2-9]\\d{7,8}$");
    private static final Pattern HOLDER_NAME_PATTERN = Pattern.compile("^[\\p{L}\\p{N} .'&/-]+$");

    @Enumerated(EnumType.STRING)
    @Column(name = "pix_key_type", length = 20)
    private PixKeyType keyType;

    @Column(name = "pix_key", length = 140)
    private String keyValue;

    @Column(name = "pix_holder_name", length = HOLDER_NAME_MAX_LENGTH)
    private String holderName;

    @Column(name = "pix_bank_name", length = BANK_NAME_MAX_LENGTH)
    private String bankName;

    protected PixKey() {
    }

    private PixKey(PixKeyType keyType, String keyValue, String holderName, String bankName) {
        this.keyType = keyType;
        this.keyValue = keyValue;
        this.holderName = holderName;
        this.bankName = bankName;
    }

    /**
     * Valida e normaliza. Falha com {@link ValidationFailedException} (400) indicando o campo.
     */
    public static PixKey of(PixKeyType keyType, String key, String holderName, String bankName) {
        if (keyType == null) {
            throw new ValidationFailedException("keyType", "Key type is required");
        }

        if (key == null || key.isBlank()) {
            throw new ValidationFailedException("key", "Key is required");
        }

        return new PixKey(
                keyType,
                normalizeKey(keyType, key.trim()),
                normalizeHolderName(holderName),
                normalizeBankName(bankName)
        );
    }

    public PixKey copy() {
        return new PixKey(keyType, keyValue, holderName, bankName);
    }

    public PixKeyType getKeyType() {
        return keyType;
    }

    public String getKeyValue() {
        return keyValue;
    }

    public String getHolderName() {
        return holderName;
    }

    public String getBankName() {
        return bankName;
    }

    // ---------- Normalização e validação ----------

    private static String normalizeKey(PixKeyType keyType, String key) {
        return switch (keyType) {
            case CPF -> normalizeCpf(key);
            case CNPJ -> normalizeCnpj(key);
            case PHONE -> normalizePhone(key);
            case EMAIL -> normalizeEmail(key);
            case RANDOM -> normalizeRandom(key);
        };
    }

    private static String normalizeCpf(String key) {
        String cpf = key.replaceAll("[.\\-\\s]", "");

        if (!CPF_PATTERN.matcher(cpf).matches() || !hasValidCpfCheckDigits(cpf)) {
            throw new ValidationFailedException("key", "Invalid CPF");
        }

        return cpf;
    }

    /**
     * CNPJ numérico ou alfanumérico (12 caracteres + 2 dígitos verificadores).
     */
    private static String normalizeCnpj(String key) {
        String cnpj = key.replaceAll("[.\\-/\\s]", "").toUpperCase(Locale.ROOT);

        if (!CNPJ_PATTERN.matcher(cnpj).matches() || !hasValidCnpjCheckDigits(cnpj)) {
            throw new ValidationFailedException("key", "Invalid CNPJ");
        }

        return cnpj;
    }

    /**
     * Celular ou fixo brasileiro; aceita máscara e o prefixo +55 (ou 55) opcional. Guarda no
     * formato {@code +55DDNNNNNNNNN}.
     */
    private static String normalizePhone(String key) {
        String digits = key.replaceAll("[^\\d]", "");

        if (digits.length() == 10 || digits.length() == 11) {
            digits = "55" + digits;
        }

        String phone = "+" + digits;

        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw new ValidationFailedException("key", "Invalid phone number");
        }

        return phone;
    }

    private static String normalizeEmail(String key) {
        String email = key.toLowerCase(Locale.ROOT);

        if (email.length() > EMAIL_MAX_LENGTH || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new ValidationFailedException("key", "Invalid email");
        }

        return email;
    }

    private static String normalizeRandom(String key) {
        String uuid = key.toLowerCase(Locale.ROOT);

        if (!UUID_PATTERN.matcher(uuid).matches()) {
            throw new ValidationFailedException("key", "Invalid random key");
        }

        return uuid;
    }

    /**
     * Nome completo de quem recebe: ao menos duas palavras, para o pagador conferir o titular.
     */
    private static String normalizeHolderName(String holderName) {
        if (holderName == null || holderName.isBlank()) {
            throw new ValidationFailedException("holderName", "Holder name is required");
        }

        String name = holderName.trim().replaceAll("\\s+", " ");

        if (name.length() > HOLDER_NAME_MAX_LENGTH) {
            throw new ValidationFailedException(
                    "holderName",
                    "Holder name must have at most " + HOLDER_NAME_MAX_LENGTH + " characters"
            );
        }

        if (!HOLDER_NAME_PATTERN.matcher(name).matches()) {
            throw new ValidationFailedException("holderName", "Holder name has invalid characters");
        }

        if (!name.contains(" ")) {
            throw new ValidationFailedException(
                    "holderName",
                    "Holder name must be the full name of the receiver"
            );
        }

        return name;
    }

    private static String normalizeBankName(String bankName) {
        if (bankName == null || bankName.isBlank()) {
            return null;
        }

        String name = bankName.trim().replaceAll("\\s+", " ");

        if (name.length() > BANK_NAME_MAX_LENGTH) {
            throw new ValidationFailedException(
                    "bankName",
                    "Bank name must have at most " + BANK_NAME_MAX_LENGTH + " characters"
            );
        }

        return name;
    }

    private static boolean hasValidCpfCheckDigits(String cpf) {
        if (cpf.chars().distinct().count() == 1) {
            return false;
        }

        int first = cpfCheckDigit(cpf, 9);
        int second = cpfCheckDigit(cpf, 10);

        return first == cpf.charAt(9) - '0' && second == cpf.charAt(10) - '0';
    }

    private static int cpfCheckDigit(String cpf, int length) {
        int sum = 0;

        for (int i = 0; i < length; i++) {
            sum += (cpf.charAt(i) - '0') * (length + 1 - i);
        }

        int remainder = (sum * 10) % 11;

        return remainder == 10 ? 0 : remainder;
    }

    private static boolean hasValidCnpjCheckDigits(String cnpj) {
        if (cnpj.chars().distinct().count() == 1) {
            return false;
        }

        int[] firstWeights = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] secondWeights = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

        int first = cnpjCheckDigit(cnpj, firstWeights);
        int second = cnpjCheckDigit(cnpj, secondWeights);

        return first == cnpj.charAt(12) - '0' && second == cnpj.charAt(13) - '0';
    }

    /**
     * Cada caractere vale seu código ASCII menos 48 (dígitos 0-9, letras A-Z = 17-42), como no
     * CNPJ alfanumérico; para CNPJ só com dígitos dá o mesmo resultado do cálculo tradicional.
     */
    private static int cnpjCheckDigit(String cnpj, int[] weights) {
        int sum = 0;

        for (int i = 0; i < weights.length; i++) {
            sum += (cnpj.charAt(i) - '0') * weights[i];
        }

        int remainder = sum % 11;

        return remainder < 2 ? 0 : 11 - remainder;
    }
}
