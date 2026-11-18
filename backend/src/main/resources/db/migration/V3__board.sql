CREATE TABLE board (
    id           uuid         PRIMARY KEY,
    workspace_id uuid         NOT NULL REFERENCES workspace (id),
    name         varchar(120) NOT NULL,
    version      bigint       NOT NULL DEFAULT 0,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_board_workspace ON board (workspace_id);
