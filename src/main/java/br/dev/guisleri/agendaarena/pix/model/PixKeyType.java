package br.dev.guisleri.agendaarena.pix.model;

/**
 * Tipos de chave Pix aceitos. {@code RANDOM} é a chave aleatória (EVP), um UUID.
 */
public enum PixKeyType {
    CPF,
    CNPJ,
    PHONE,
    EMAIL,
    RANDOM
}
