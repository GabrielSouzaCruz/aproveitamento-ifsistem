package ifsuldeminas.edu.br.ifsystem.aproveitamento.api;

import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.Anexo;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.DisciplinaCursada;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.DisciplinaCurso;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.HistoricoStatus;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.ParecerIa;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.PreDiagnostico;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.RecomendacaoIa;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.Solicitacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.StatusParecerIa;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.StatusPreDiagnostico;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.StatusSolicitacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.StatusVerificacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.TipoDetectado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AproveitamentoDtos {
    private AproveitamentoDtos() {
    }

    // ---------- Entrada ----------

    public record CriarSolicitacao(
            @NotNull UUID alunoId,
            @NotNull UUID disciplinaDestinoId,
            @NotEmpty List<@Valid DisciplinaCursadaEntrada> disciplinas) {
    }

    /** Edicao de rascunho/pendencia: novo destino e a lista completa (substitui a anterior). */
    public record AtualizarSolicitacao(
            @NotNull UUID disciplinaDestinoId,
            @NotEmpty List<@Valid DisciplinaCursadaEntrada> disciplinas) {
    }

    public record DisciplinaCursadaEntrada(
            @NotBlank String nome,
            @NotBlank String instituicao,
            @NotNull @Positive Integer cargaHoraria,
            @NotBlank String ementa) {
    }

    public record AcaoRequest(@Size(max = 2000) String observacao) {
    }

    // ---------- Saida ----------

    public record SolicitacaoResumo(UUID id, UUID alunoId, String curso, String disciplinaDestino,
                                    StatusSolicitacao status, OffsetDateTime atualizadoEm) {
        public static SolicitacaoResumo de(Solicitacao s) {
            return new SolicitacaoResumo(s.getId(), s.getAlunoId(), s.getCurso(), s.getDisciplinaDestino(),
                    s.getStatus(), s.getAtualizadoEm());
        }
    }

    public record DisciplinaCursadaResposta(UUID id, String nome, String instituicao, Integer cargaHoraria,
                                            String ementa) {
        public static DisciplinaCursadaResposta de(DisciplinaCursada d) {
            return new DisciplinaCursadaResposta(d.getId(), d.getNome(), d.getInstituicao(), d.getCargaHoraria(),
                    d.getEmenta());
        }
    }

    /** {@code motivoVerificacao} e {@code tipoDetectado} so vao para SERVIDOR e professor (nulos para o aluno). */
    public record AnexoResposta(UUID id, String nomeArquivo, Long tamanho, OffsetDateTime criadoEm,
                                StatusVerificacao statusVerificacao, TipoDetectado tipoDetectado,
                                String motivoVerificacao) {
        /** Visao do aluno: so o selo. */
        public static AnexoResposta de(Anexo a) {
            return de(a, false);
        }

        public static AnexoResposta de(Anexo a, boolean comMotivo) {
            return new AnexoResposta(a.getId(), a.getNomeArquivo(), a.getTamanho(), a.getCriadoEm(),
                    a.getStatusVerificacao(), comMotivo ? a.getTipoDetectado() : null,
                    comMotivo ? a.getMotivoVerificacao() : null);
        }
    }

    /** Nao expoe prompt nem modelo. */
    public record PreDiagnosticoResposta(StatusPreDiagnostico status, BigDecimal percentualCarga,
                                         BigDecimal percentualConteudo, String justificativa,
                                         OffsetDateTime criadoEm) {
        public static PreDiagnosticoResposta de(PreDiagnostico p) {
            return new PreDiagnosticoResposta(p.getStatus(), p.getPercentualCarga(), p.getPercentualConteudo(),
                    p.getJustificativa(), p.getCriadoEm());
        }
    }

    /** {@code cursoId} e {@code ementaDestino} sao nulos nas solicitacoes anteriores ao catalogo.
     *  {@code preDiagnostico} e nulo quando a solicitacao ainda nao tem pre-diagnostico.
     *  {@code podeDecidir} e true so para o professor coordenador do curso da solicitacao. */
    public record SolicitacaoDetalhe(UUID id, UUID alunoId, String curso, UUID cursoId, String disciplinaDestino,
                                     Integer cargaHorariaDestino, String ementaDestino, StatusSolicitacao status,
                                     OffsetDateTime criadoEm, OffsetDateTime atualizadoEm,
                                     List<DisciplinaCursadaResposta> disciplinas,
                                     List<AnexoResposta> anexos,
                                     PreDiagnosticoResposta preDiagnostico,
                                     boolean podeDecidir) {
        public static SolicitacaoDetalhe de(Solicitacao s, List<DisciplinaCursada> disciplinas,
                                            List<Anexo> anexos, PreDiagnostico preDiagnostico) {
            return de(s, disciplinas, anexos, preDiagnostico, false);
        }

        public static SolicitacaoDetalhe de(Solicitacao s, List<DisciplinaCursada> disciplinas,
                                            List<Anexo> anexos, PreDiagnostico preDiagnostico,
                                            boolean podeDecidir) {
            return de(s, disciplinas, anexos, preDiagnostico, podeDecidir, false);
        }

        /** {@code verMotivoAnexos}: perfis que enxergam o motivo da verificacao (SERVIDOR e professor). */
        public static SolicitacaoDetalhe de(Solicitacao s, List<DisciplinaCursada> disciplinas,
                                            List<Anexo> anexos, PreDiagnostico preDiagnostico,
                                            boolean podeDecidir, boolean verMotivoAnexos) {
            DisciplinaCurso destino = s.getDisciplinaCursoDestino();
            return new SolicitacaoDetalhe(s.getId(), s.getAlunoId(), s.getCurso(),
                    destino == null ? null : destino.getCurso().getId(), s.getDisciplinaDestino(),
                    s.getCargaHorariaDestino(), destino == null ? null : destino.getEmenta(), s.getStatus(), s.getCriadoEm(), s.getAtualizadoEm(),
                    disciplinas.stream().map(DisciplinaCursadaResposta::de).toList(),
                    anexos.stream().map(a -> AnexoResposta.de(a, verMotivoAnexos)).toList(),
                    preDiagnostico == null ? null : PreDiagnosticoResposta.de(preDiagnostico),
                    podeDecidir);
        }
    }

    public record HistoricoItem(StatusSolicitacao statusAnterior, StatusSolicitacao statusNovo,
                                UUID responsavelId, String observacao, OffsetDateTime criadoEm) {
        public static HistoricoItem de(HistoricoStatus h) {
            return new HistoricoItem(h.getStatusAnterior(), h.getStatusNovo(), h.getResponsavelId(),
                    h.getObservacao(), h.getCriadoEm());
        }
    }

    public record ParecerIaResposta(UUID id, StatusParecerIa status, RecomendacaoIa recomendacao,
                                    Integer similaridade, String justificativa, String modelo,
                                    OffsetDateTime geradoEm, String erro) {
        public static ParecerIaResposta de(ParecerIa p) {
            return new ParecerIaResposta(p.getId(), p.getStatus(), p.getRecomendacao(), p.getSimilaridade(),
                    p.getJustificativa(), p.getModelo(), p.getGeradoEm(), p.getErro());
        }
    }
}
