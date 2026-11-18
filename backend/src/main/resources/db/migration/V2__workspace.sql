CREATE TABLE workspace (
    id         uuid         PRIMARY KEY,
    name       varchar(120) NOT NULL,
    version    bigint       NOT NULL DEFAULT 0,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);
