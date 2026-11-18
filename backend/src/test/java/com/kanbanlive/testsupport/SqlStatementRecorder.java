package com.kanbanlive.testsupport;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Registra el SQL que Hibernate prepara, para afirmar cuantas sentencias emite un caso de uso. Se activa en un
 * test con {@code spring.jpa.properties.hibernate.session_factory.statement_inspector}. Estado estatico: llamar a
 * {@link #clear()} antes de la accion a medir (los tests de un contexto no corren en paralelo).
 */
public class SqlStatementRecorder implements StatementInspector {

    private static final List<String> STATEMENTS = new CopyOnWriteArrayList<>();

    public static final String PROPERTY =
            "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.kanbanlive.testsupport.SqlStatementRecorder";

    public static void clear() {
        STATEMENTS.clear();
    }

    public static List<String> statements() {
        return List.copyOf(STATEMENTS);
    }

    /** Sentencias que empiezan por el verbo dado (insensible a mayusculas), p. ej. "update". */
    public static List<String> statementsStartingWith(String verb) {
        return STATEMENTS.stream().filter(s -> s.stripLeading().toLowerCase().startsWith(verb.toLowerCase()))
                .toList();
    }

    @Override
    public String inspect(String sql) {
        STATEMENTS.add(sql);
        return sql;
    }
}
