package com.kanbanlive.sharedkernel.position;

import com.kanbanlive.sharedkernel.domain.ValidationException;
import com.kanbanlive.sharedkernel.domain.ValueObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Posicion ordenable estilo LexoRank: un numero fraccionario 0.d1d2d3... en base 62 representado como string.
 *
 * <p>Alfabeto {@code 0-9A-Za-z}, en el mismo orden que ASCII, de modo que el orden natural de {@link String}
 * coincide con {@code ORDER BY position COLLATE "C"} de Postgres. Invariantes: no vacio, solo caracteres del
 * alfabeto y sin '0' final (forma canonica; asi cada valor tiene una unica representacion y siempre existe
 * un valor estrictamente entre dos distintos).
 *
 * <p>Mover una tarjeta solo cambia su propia posicion ({@link #between}); nunca se reescriben las vecinas.
 * Cuando las claves crecen demasiado ({@link #isExhausted()}) se rebalancea la lista con
 * {@link #evenlySpaced(int)} (ver ADR-0003).
 */
public record FractionalIndex(String value) implements ValueObject, Comparable<FractionalIndex> {

    public static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    public static final int BASE = ALPHABET.length();
    /** Longitud a partir de la cual se considera agotado el espacio y conviene rebalancear. */
    public static final int EXHAUSTION_LENGTH = 32;
    /** Longitud maxima admitida (cabe holgada en varchar(64) de la columna). */
    public static final int MAX_LENGTH = 64;

    public FractionalIndex {
        if (value == null || value.isEmpty()) {
            throw new ValidationException("La posicion no puede estar vacia");
        }
        if (value.length() > MAX_LENGTH) {
            throw new ValidationException("La posicion supera " + MAX_LENGTH + " caracteres");
        }
        for (int i = 0; i < value.length(); i++) {
            if (ALPHABET.indexOf(value.charAt(i)) < 0) {
                throw new ValidationException("Caracter invalido en la posicion: '" + value.charAt(i) + "'");
            }
        }
        if (value.charAt(value.length() - 1) == ALPHABET.charAt(0)) {
            throw new ValidationException("La posicion no puede terminar en '0'");
        }
    }

    public static FractionalIndex of(String value) {
        return new FractionalIndex(value);
    }

    /**
     * Posicion estrictamente entre {@code prev} y {@code next}. {@code null} significa "sin limite":
     * {@code prev == null} inserta al principio, {@code next == null} al final, ambos null en lista vacia.
     */
    public static FractionalIndex between(FractionalIndex prev, FractionalIndex next) {
        if (prev != null && next != null && prev.compareTo(next) >= 0) {
            throw new ValidationException("prev debe ser estrictamente menor que next");
        }
        String a = prev == null ? "" : prev.value;
        String b = next == null ? null : next.value;
        return new FractionalIndex(midpoint(a, b));
    }

    /** {@code count} posiciones crecientes y equiespaciadas, con holgura para inserciones posteriores. */
    public static List<FractionalIndex> evenlySpaced(int count) {
        if (count < 0) {
            throw new ValidationException("count no puede ser negativo");
        }
        List<FractionalIndex> result = new ArrayList<>(count);
        if (count == 0) {
            return result;
        }
        // k digitos tales que BASE^k >= 4 * (count + 1): al menos ~3 huecos libres entre vecinas.
        int k = 1;
        long space = BASE;
        long needed = 4L * (count + 1);
        while (space < needed) {
            k++;
            space *= BASE;
        }
        long step = space / (count + 1);
        for (int i = 1; i <= count; i++) {
            result.add(new FractionalIndex(encode(step * i, k)));
        }
        return result;
    }

    /** True si la clave es lo bastante larga como para que convenga rebalancear la lista. */
    public boolean isExhausted() {
        return value.length() >= EXHAUSTION_LENGTH;
    }

    @Override
    public int compareTo(FractionalIndex other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }

    private static String encode(long number, int digits) {
        char[] chars = new char[digits];
        for (int i = digits - 1; i >= 0; i--) {
            chars[i] = ALPHABET.charAt((int) (number % BASE));
            number /= BASE;
        }
        int end = digits;
        while (end > 1 && chars[end - 1] == ALPHABET.charAt(0)) {
            end--;
        }
        return new String(chars, 0, end);
    }

    /** a: fraccion inferior ("" = 0); b: fraccion superior o null (= 1). Precondicion a < b. */
    private static String midpoint(String a, String b) {
        if (b != null) {
            int n = 0;
            while (n < b.length() && digitAt(a, n) == digitAt(b, n)) {
                n++;
            }
            if (n > 0) {
                return b.substring(0, n) + midpoint(a.length() > n ? a.substring(n) : "", b.substring(n));
            }
        }
        int digitA = digitAt(a, 0);
        int digitB = b == null ? BASE : ALPHABET.indexOf(b.charAt(0));
        if (digitB - digitA > 1) {
            return String.valueOf(ALPHABET.charAt((digitA + digitB + 1) / 2));
        }
        if (b != null && b.length() > 1) {
            return b.substring(0, 1);
        }
        return ALPHABET.charAt(digitA) + midpoint(a.length() > 1 ? a.substring(1) : "", null);
    }

    private static int digitAt(String s, int i) {
        return i < s.length() ? ALPHABET.indexOf(s.charAt(i)) : 0;
    }
}
