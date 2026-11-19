-- Fase 4 (B4.3): retencion del log de eventos. La purga borra por created_at en lotes; sin este indice cada lote
-- haria un seq scan de board_event. Ver ADR-0005 / ADR-0008.
CREATE INDEX idx_board_event_created_at ON board_event (created_at);
