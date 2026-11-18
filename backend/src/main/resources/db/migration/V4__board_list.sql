CREATE TABLE board_list (
    id         uuid                  PRIMARY KEY,
    board_id   uuid                  NOT NULL REFERENCES board (id),
    name       varchar(120)          NOT NULL,
    position   varchar(64) COLLATE "C" NOT NULL,
    version    bigint                NOT NULL DEFAULT 0,
    created_at timestamptz           NOT NULL DEFAULT now(),
    updated_at timestamptz           NOT NULL DEFAULT now()
);

CREATE INDEX idx_board_list_board_position ON board_list (board_id, position);
