CREATE TABLE card (
    id          uuid                  PRIMARY KEY,
    list_id     uuid                  NOT NULL REFERENCES board_list (id),
    title       varchar(255)          NOT NULL,
    description text,
    position    varchar(64) COLLATE "C" NOT NULL,
    version     bigint                NOT NULL DEFAULT 0,
    created_at  timestamptz           NOT NULL DEFAULT now(),
    updated_at  timestamptz           NOT NULL DEFAULT now()
);

CREATE INDEX idx_card_list_position ON card (list_id, position);
