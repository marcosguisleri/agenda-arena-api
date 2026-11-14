package br.dev.guisleri.agendaarena.pix;

import br.dev.guisleri.agendaarena.common.exception.ValidationFailedException;
import br.dev.guisleri.agendaarena.pix.entity.PixKey;
import br.dev.guisleri.agendaarena.pix.model.PixKeyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PixKeyTests {

    private static final String HOLDER = "Maria da Silva";

    private static PixKey key(PixKeyType type, String value) {
        return PixKey.of(type, value, HOLDER, null);
    }

    private static void assertInvalid(PixKeyType type, String value, String field) {
        assertThatThrownBy(() -> key(type, value))
                .isInstanceOfSatisfying(ValidationFailedException.class, exception ->
                        assertThat(exception.getField()).isEqualTo(field));
    }

    @Test
    @DisplayName("CPF: aceita máscara, guarda só dígitos e confere os dígitos verificadores")
    void cpf() {
        assertThat(key(PixKeyType.CPF, "529.982.247-25").getKeyValue()).isEqualTo("52998224725");
        assertThat(key(PixKeyType.CPF, " 11144477735 ").getKeyValue()).isEqualTo("11144477735");

        assertInvalid(PixKeyType.CPF, "529.982.247-26", "key");
        assertInvalid(PixKeyType.CPF, "111.111.111-11", "key");
        assertInvalid(PixKeyType.CPF, "1234567890", "key");
        assertInvalid(PixKeyType.CPF, "abcdefghijk", "key");
    }

    @Test
    @DisplayName("CNPJ: numérico e alfanumérico, com máscara, em maiúsculas")
    void cnpj() {
        assertThat(key(PixKeyType.CNPJ, "11.222.333/0001-81").getKeyValue())
                .isEqualTo("11222333000181");
        assertThat(key(PixKeyType.CNPJ, "12.ABC.345/01DE-35").getKeyValue())
                .isEqualTo("12ABC34501DE35");
        assertThat(key(PixKeyType.CNPJ, "12.abc.345/01de-35").getKeyValue())
                .isEqualTo("12ABC34501DE35");

        assertInvalid(PixKeyType.CNPJ, "11.222.333/0001-82", "key");
        assertInvalid(PixKeyType.CNPJ, "12.ABC.345/01DE-36", "key");
        assertInvalid(PixKeyType.CNPJ, "00.000.000/0000-00", "key");
        assertInvalid(PixKeyType.CNPJ, "1122233300018", "key");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @DisplayName("Telefone: normaliza para +55DDNNNNNNNNN")
    @CsvSource(delimiter = '|', value = {
            "(19) 99999-8888|+5519999998888",
            "+55 19 99999-8888|+5519999998888",
            "5519999998888|+5519999998888",
            "19999998888|+5519999998888",
            "(19) 3541-2233|+551935412233"
    })
    void phone(String input, String expected) {
        assertThat(key(PixKeyType.PHONE, input).getKeyValue()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("Telefone inválido")
    @CsvSource({"123", "01999998888", "(19) 1999-8888", "abc", "+1 202 555 0100"})
    void invalidPhone(String input) {
        assertInvalid(PixKeyType.PHONE, input, "key");
    }

    @Test
    @DisplayName("E-mail: minúsculas, sem espaços, até 77 caracteres")
    void email() {
        assertThat(key(PixKeyType.EMAIL, "  Maria@Example.COM ").getKeyValue())
                .isEqualTo("maria@example.com");

        assertInvalid(PixKeyType.EMAIL, "sem-arroba", "key");
        assertInvalid(PixKeyType.EMAIL, "a@b", "key");
        assertInvalid(PixKeyType.EMAIL, "a".repeat(70) + "@example.com", "key");
    }

    @Test
    @DisplayName("Chave aleatória: UUID, em minúsculas")
    void random() {
        assertThat(key(PixKeyType.RANDOM, "123E4567-E89B-12D3-A456-426614174000").getKeyValue())
                .isEqualTo("123e4567-e89b-12d3-a456-426614174000");

        assertInvalid(PixKeyType.RANDOM, "not-a-uuid", "key");
        assertInvalid(PixKeyType.RANDOM, "123e4567e89b12d3a456426614174000", "key");
    }

    @Test
    @DisplayName("Titular: nome completo (duas palavras ou mais), espaços normalizados")
    void holderName() {
        assertThat(PixKey.of(PixKeyType.EMAIL, "a@b.co", "  Maria   da  Silva ", null)
                .getHolderName()).isEqualTo("Maria da Silva");
        assertThat(PixKey.of(PixKeyType.EMAIL, "a@b.co", "Arena Central Ltda.", null)
                .getHolderName()).isEqualTo("Arena Central Ltda.");
        assertThat(PixKey.of(PixKeyType.EMAIL, "a@b.co", "José d'Ávila-Souza", null)
                .getHolderName()).isEqualTo("José d'Ávila-Souza");

        for (String invalid : new String[]{null, "", "   ", "Maria", "Maria <b>Silva</b>",
                "Maria " + "a".repeat(120)}) {
            assertThatThrownBy(() -> PixKey.of(PixKeyType.EMAIL, "a@b.co", invalid, null))
                    .isInstanceOfSatisfying(ValidationFailedException.class, exception ->
                            assertThat(exception.getField()).isEqualTo("holderName"));
        }
    }

    @Test
    @DisplayName("Banco: opcional, vazio vira nulo, até 80 caracteres")
    void bankName() {
        assertThat(PixKey.of(PixKeyType.EMAIL, "a@b.co", HOLDER, "  Banco  do Brasil ")
                .getBankName()).isEqualTo("Banco do Brasil");
        assertThat(PixKey.of(PixKeyType.EMAIL, "a@b.co", HOLDER, "   ").getBankName()).isNull();
        assertThat(PixKey.of(PixKeyType.EMAIL, "a@b.co", HOLDER, null).getBankName()).isNull();

        assertThatThrownBy(() ->
                PixKey.of(PixKeyType.EMAIL, "a@b.co", HOLDER, "b".repeat(81)))
                .isInstanceOfSatisfying(ValidationFailedException.class, exception ->
                        assertThat(exception.getField()).isEqualTo("bankName"));
    }

    @Test
    @DisplayName("Tipo e chave são obrigatórios")
    void requiredFields() {
        assertThatThrownBy(() -> PixKey.of(null, "a@b.co", HOLDER, null))
                .isInstanceOfSatisfying(ValidationFailedException.class, exception ->
                        assertThat(exception.getField()).isEqualTo("keyType"));
        assertInvalid(PixKeyType.EMAIL, null, "key");
        assertInvalid(PixKeyType.EMAIL, "   ", "key");
    }

    @Test
    @DisplayName("copy gera outra instância com os mesmos valores")
    void copy() {
        PixKey original = PixKey.of(PixKeyType.EMAIL, "a@b.co", HOLDER, "Banco X");

        PixKey copy = original.copy();

        assertThat(copy).isNotSameAs(original);
        assertThat(copy.getKeyType()).isEqualTo(PixKeyType.EMAIL);
        assertThat(copy.getKeyValue()).isEqualTo("a@b.co");
        assertThat(copy.getHolderName()).isEqualTo(HOLDER);
        assertThat(copy.getBankName()).isEqualTo("Banco X");
    }
}
