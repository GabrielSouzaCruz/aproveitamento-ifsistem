-- "Solicitar novamente": nova solicitacao criada a partir de uma indeferida.
-- O UNIQUE garante uma unica nova tentativa por indeferimento.
ALTER TABLE solicitacoes_aproveitamento ADD COLUMN solicitacao_origem_id uuid;

ALTER TABLE solicitacoes_aproveitamento
    ADD CONSTRAINT fk_solicitacao_origem FOREIGN KEY (solicitacao_origem_id) REFERENCES solicitacoes_aproveitamento (id);

ALTER TABLE solicitacoes_aproveitamento
    ADD CONSTRAINT uk_solicitacao_origem UNIQUE (solicitacao_origem_id);
