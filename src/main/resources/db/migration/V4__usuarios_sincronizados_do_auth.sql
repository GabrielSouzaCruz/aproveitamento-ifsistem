-- public.usuarios passa a ser copia de leitura dos usuarios do ifsistem-auth (id = sub do token).
-- Usuarios novos entram so com nome, cpf e perfil: as colunas legadas deixam de ser obrigatorias.
-- A coluna senha (hash BCrypt do login antigo) nao e mais usada.
ALTER TABLE usuarios ALTER COLUMN email DROP NOT NULL;
ALTER TABLE usuarios ALTER COLUMN matricula DROP NOT NULL;
ALTER TABLE usuarios ALTER COLUMN senha DROP NOT NULL;
UPDATE usuarios SET senha = NULL;
