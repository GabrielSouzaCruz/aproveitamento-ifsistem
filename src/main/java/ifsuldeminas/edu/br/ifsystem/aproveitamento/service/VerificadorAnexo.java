package ifsuldeminas.edu.br.ifsystem.aproveitamento.service;

import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.StatusVerificacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.TipoDetectado;

/** Porta da verificacao automatica de anexos (ementa ou historico em PDF). */
public interface VerificadorAnexo {

    ResultadoVerificacao verificar(EntradaVerificacao entrada);

    /**
     * Disciplina de origem informada pelo aluno, texto extraido do PDF (pode ser vazio, PDF escaneado)
     * e os bytes do PDF (usados quando nao ha texto suficiente).
     */
    record EntradaVerificacao(String disciplina, String instituicao, Integer cargaHoraria, String ementa,
                              String textoExtraido, byte[] pdf) {
    }

    /** {@code status} nunca e VERIFICANDO. {@code textoTranscrito} so vem quando o PDF foi enviado ao modelo. */
    record ResultadoVerificacao(TipoDetectado tipoDetectado, StatusVerificacao status, String motivo,
                                Integer cargaHorariaEncontrada, String textoTranscrito, String modelo) {
    }
}
