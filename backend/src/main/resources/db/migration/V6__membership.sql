CREATE TABLE membership (
    id           uuid         PRIMARY KEY,
    workspace_id uuid         NOT NULL REFERENCES workspace (id) ON DELETE CASCADE,
    user_id      varchar(255) NOT NULL,
    role         varchar(16)  NOT NULL CHECK (role IN ('OWNER', 'EDITOR', 'VIEWER')),
    version      bigint       NOT NULL DEFAULT 0,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_membership_workspace_user UNIQUE (workspace_id, user_id)
);

CREATE INDEX idx_membership_user ON membership (user_id);
