-- Verificacao automatica dos anexos (texto extraido do PDF + analise por IA ou heuristica local).
ALTER TABLE anexos_aproveitamento ADD COLUMN texto_extraido       text;
ALTER TABLE anexos_aproveitamento ADD COLUMN status_verificacao   varchar(255);
ALTER TABLE anexos_aproveitamento ADD COLUMN motivo_verificacao   text;
ALTER TABLE anexos_aproveitamento ADD COLUMN tipo_detectado       varchar(255);
ALTER TABLE anexos_aproveitamento ADD COLUMN verificado_em        timestamptz;
ALTER TABLE anexos_aproveitamento ADD COLUMN modelo_verificacao   varchar(255);

-- Anexos anteriores a esta versao nunca foram verificados: ficam para conferencia manual.
UPDATE anexos_aproveitamento
   SET status_verificacao = 'PRECISA_CONFERIR',
       motivo_verificacao = 'Anexo enviado antes da verificacao automatica.'
 WHERE status_verificacao IS NULL;

ALTER TABLE anexos_aproveitamento ALTER COLUMN status_verificacao SET NOT NULL;
