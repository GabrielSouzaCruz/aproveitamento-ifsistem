package ifsuldeminas.edu.br.ifsystem.aproveitamento.service;

import java.util.UUID;

/** Publicado quando um anexo e gravado (ou reverificacao e pedida); a verificacao roda apos o commit. */
public record AnexoParaVerificarEvent(UUID anexoId) {
}
