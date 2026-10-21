package com.komainu.moxia.share;

/**
 * Una cosa que exportar, escrita {@code tipo:nombre} ({@code paleta:rocas}, {@code plano:servidor/torre}).
 * {@code tipo:*} son todas las de ese tipo.
 */
public record ShareRef(ShareKind kind, String name) {
    public static final String ALL = "*";

    public boolean all() {
        return name.equals(ALL);
    }

    @Override
    public String toString() {
        return kind.id() + ":" + name;
    }
}
