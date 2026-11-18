-- Fase 3 (B3.3): secuencia por tablero + log de eventos. Ver ADR-0008.
ALTER TABLE board ADD COLUMN event_seq bigint NOT NULL DEFAULT 0;

CREATE TABLE board_event (
    board_id   uuid        NOT NULL REFERENCES board (id) ON DELETE CASCADE,
    seq        bigint      NOT NULL,
    type       varchar(64) NOT NULL,
    payload    jsonb       NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    -- Unicidad de (board_id, seq): garantiza orden total por tablero y sirve de indice de resync (seq > N).
    CONSTRAINT pk_board_event PRIMARY KEY (board_id, seq)
);
