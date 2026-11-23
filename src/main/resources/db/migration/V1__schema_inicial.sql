-- Schema inicial (PostgreSQL). Equivalente ao que o Hibernate gerava com ddl-auto=update.
-- Enums sao guardados como varchar(255) (EnumType.STRING), sem check constraint.

CREATE TABLE usuarios (
    id            uuid         NOT NULL,
    nome          varchar(255) NOT NULL,
    cpf           varchar(11)  NOT NULL,
    email         varchar(255) NOT NULL,
    matricula     varchar(255) NOT NULL,
    senha         varchar(255) NOT NULL,
    telefone      varchar(255),
    curso         varchar(255),
    periodo       integer,
    perfil        varchar(255) NOT NULL,
    status        varchar(255) NOT NULL,
    criado_em     timestamptz  NOT NULL,
    atualizado_em timestamptz  NOT NULL,
    CONSTRAINT pk_usuarios PRIMARY KEY (id),
    CONSTRAINT uk_usuario_cpf UNIQUE (cpf),
    CONSTRAINT uk_usuario_email UNIQUE (email),
    CONSTRAINT uk_usuario_matricula UNIQUE (matricula)
);

CREATE TABLE cursos (
    id     uuid         NOT NULL,
    codigo varchar(255) NOT NULL,
    nome   varchar(255) NOT NULL,
    ativo  boolean      NOT NULL,
    CONSTRAINT pk_cursos PRIMARY KEY (id),
    CONSTRAINT uk_cursos_codigo UNIQUE (codigo)
);

CREATE TABLE coordenadores_aproveitamento (
    id           uuid        NOT NULL,
    professor_id uuid        NOT NULL,
    curso_id     uuid        NOT NULL,
    criado_em    timestamptz NOT NULL,
    CONSTRAINT pk_coordenadores_aproveitamento PRIMARY KEY (id),
    CONSTRAINT uk_coordenador_professor_curso UNIQUE (professor_id, curso_id),
    CONSTRAINT fk_coordenador_curso FOREIGN KEY (curso_id) REFERENCES cursos (id)
);

CREATE TABLE disciplinas_curso (
    id            uuid         NOT NULL,
    curso_id      uuid         NOT NULL,
    codigo        varchar(255) NOT NULL,
    nome          varchar(255) NOT NULL,
    carga_horaria integer      NOT NULL,
    ementa        text         NOT NULL,
    ativo         boolean      NOT NULL,
    CONSTRAINT pk_disciplinas_curso PRIMARY KEY (id),
    CONSTRAINT uk_disciplina_curso_codigo UNIQUE (curso_id, codigo),
    CONSTRAINT fk_disciplina_curso_curso FOREIGN KEY (curso_id) REFERENCES cursos (id)
);

CREATE TABLE solicitacoes_aproveitamento (
    id                    uuid         NOT NULL,
    aluno_id              uuid         NOT NULL,
    curso                 varchar(255) NOT NULL,
    disciplina_destino    varchar(255) NOT NULL,
    carga_horaria_destino integer      NOT NULL,
    disciplina_destino_id uuid,
    status                varchar(255) NOT NULL,
    criado_em             timestamptz  NOT NULL,
    atualizado_em         timestamptz  NOT NULL,
    version               bigint,
    CONSTRAINT pk_solicitacoes_aproveitamento PRIMARY KEY (id),
    CONSTRAINT fk_solicitacao_disciplina_destino FOREIGN KEY (disciplina_destino_id) REFERENCES disciplinas_curso (id)
);

CREATE TABLE disciplinas_cursadas (
    id             uuid         NOT NULL,
    solicitacao_id uuid         NOT NULL,
    nome           varchar(255) NOT NULL,
    instituicao    varchar(255) NOT NULL,
    carga_horaria  integer      NOT NULL,
    ementa         text,
    CONSTRAINT pk_disciplinas_cursadas PRIMARY KEY (id),
    CONSTRAINT fk_disciplina_cursada_solicitacao FOREIGN KEY (solicitacao_id) REFERENCES solicitacoes_aproveitamento (id)
);

CREATE TABLE anexos_aproveitamento (
    id             uuid         NOT NULL,
    solicitacao_id uuid         NOT NULL,
    nome_arquivo   varchar(255) NOT NULL,
    tipo_conteudo  varchar(255) NOT NULL,
    tamanho        bigint       NOT NULL,
    criado_em      timestamptz  NOT NULL,
    CONSTRAINT pk_anexos_aproveitamento PRIMARY KEY (id),
    CONSTRAINT fk_anexo_solicitacao FOREIGN KEY (solicitacao_id) REFERENCES solicitacoes_aproveitamento (id)
);

CREATE TABLE anexos_conteudo (
    id       uuid  NOT NULL,
    conteudo bytea NOT NULL,
    CONSTRAINT pk_anexos_conteudo PRIMARY KEY (id),
    CONSTRAINT fk_anexo_conteudo_anexo FOREIGN KEY (id) REFERENCES anexos_aproveitamento (id)
);

CREATE TABLE historico_status (
    id              uuid         NOT NULL,
    solicitacao_id  uuid         NOT NULL,
    status_anterior varchar(255),
    status_novo     varchar(255) NOT NULL,
    responsavel_id  uuid,
    observacao      varchar(2000),
    criado_em       timestamptz  NOT NULL,
    CONSTRAINT pk_historico_status PRIMARY KEY (id),
    CONSTRAINT fk_historico_solicitacao FOREIGN KEY (solicitacao_id) REFERENCES solicitacoes_aproveitamento (id)
);

CREATE TABLE pareceres_ia (
    id             uuid         NOT NULL,
    solicitacao_id uuid         NOT NULL,
    status         varchar(255) NOT NULL,
    recomendacao   varchar(255),
    similaridade   integer,
    justificativa  text,
    modelo         varchar(255),
    gerado_em      timestamptz  NOT NULL,
    erro           varchar(255),
    CONSTRAINT pk_pareceres_ia PRIMARY KEY (id),
    CONSTRAINT fk_parecer_solicitacao FOREIGN KEY (solicitacao_id) REFERENCES solicitacoes_aproveitamento (id)
);

CREATE TABLE pre_diagnosticos (
    id                  uuid         NOT NULL,
    solicitacao_id      uuid         NOT NULL,
    status              varchar(255) NOT NULL,
    percentual_carga    numeric(5,2),
    percentual_conteudo numeric(5,2),
    justificativa       text,
    prompt              text,
    modelo              varchar(255),
    criado_em           timestamptz  NOT NULL,
    CONSTRAINT pk_pre_diagnosticos PRIMARY KEY (id),
    CONSTRAINT fk_pre_diagnostico_solicitacao FOREIGN KEY (solicitacao_id) REFERENCES solicitacoes_aproveitamento (id)
);

-- Indices nas FKs e nos filtros mais usados (o Postgres nao indexa FKs sozinho).
CREATE INDEX idx_solicitacoes_aluno ON solicitacoes_aproveitamento (aluno_id);
CREATE INDEX idx_solicitacoes_status ON solicitacoes_aproveitamento (status);
CREATE INDEX idx_solicitacoes_disciplina_destino ON solicitacoes_aproveitamento (disciplina_destino_id);
CREATE INDEX idx_disciplinas_cursadas_solicitacao ON disciplinas_cursadas (solicitacao_id);
CREATE INDEX idx_anexos_solicitacao ON anexos_aproveitamento (solicitacao_id);
CREATE INDEX idx_historico_solicitacao ON historico_status (solicitacao_id);
CREATE INDEX idx_pareceres_solicitacao ON pareceres_ia (solicitacao_id);
CREATE INDEX idx_pre_diagnosticos_solicitacao ON pre_diagnosticos (solicitacao_id);
CREATE INDEX idx_coordenadores_curso ON coordenadores_aproveitamento (curso_id);
