package ifsuldeminas.edu.br.ifsystem.aproveitamento.api;

import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.AproveitamentoDtos.AcaoRequest;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.AproveitamentoDtos.AnexoResposta;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.AproveitamentoDtos.CriarSolicitacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.AproveitamentoDtos.DisciplinaCursadaEntrada;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.AproveitamentoDtos.DisciplinaCursadaResposta;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.AproveitamentoDtos.HistoricoItem;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.AproveitamentoDtos.PreDiagnosticoResposta;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.AproveitamentoDtos.SolicitacaoDetalhe;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.AproveitamentoDtos.SolicitacaoResumo;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.Anexo;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.StatusVerificacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.TipoDetectado;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.DisciplinaCursada;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.HistoricoStatus;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.PreDiagnostico;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.Solicitacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.StatusPreDiagnostico;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.StatusSolicitacao;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AproveitamentoDtosTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void iniciarValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void fecharValidator() {
        factory.close();
    }

    private static <T> T comId(T entidade) {
        ReflectionTestUtils.setField(entidade, "id", UUID.randomUUID());
        return entidade;
    }

    private static Solicitacao solicitacao() {
        Solicitacao s = comId(new Solicitacao(UUID.randomUUID(), "ADS", "Banco de Dados", 60));
        s.alterarStatus(StatusSolicitacao.ENVIADA);
        return s;
    }

    // ---------- Mapeamento de(...) ----------

    @Test
    void solicitacaoResumoDe() {
        Solicitacao s = solicitacao();
        SolicitacaoResumo r = SolicitacaoResumo.de(s);
        assertThat(r).isEqualTo(new SolicitacaoResumo(s.getId(), s.getAlunoId(), "ADS", "Banco de Dados",
                StatusSolicitacao.ENVIADA, s.getAtualizadoEm()));
    }

    @Test
    void disciplinaAnexoEHistoricoDe() {
        Solicitacao s = solicitacao();
        DisciplinaCursada d = comId(new DisciplinaCursada(s, "BD I", "UFLA", 40, "modelagem sql"));
        Anexo a = comId(new Anexo(s, "historico.pdf", "application/pdf", 1234L));
        UUID responsavel = UUID.randomUUID();
        HistoricoStatus h = comId(new HistoricoStatus(s, StatusSolicitacao.RASCUNHO,
                StatusSolicitacao.ENVIADA, responsavel, "Enviada"));

        assertThat(DisciplinaCursadaResposta.de(d))
                .isEqualTo(new DisciplinaCursadaResposta(d.getId(), "BD I", "UFLA", 40, "modelagem sql"));
        assertThat(AnexoResposta.de(a))
                .isEqualTo(new AnexoResposta(a.getId(), "historico.pdf", 1234L, a.getCriadoEm(),
                        StatusVerificacao.VERIFICANDO, null, null));
        assertThat(HistoricoItem.de(h)).isEqualTo(new HistoricoItem(StatusSolicitacao.RASCUNHO,
                StatusSolicitacao.ENVIADA, responsavel, "Enviada", h.getCriadoEm()));
    }

    @Test
    void preDiagnosticoRespostaNaoExpoePromptNemModelo() {
        PreDiagnostico p = comId(new PreDiagnostico(solicitacao(), StatusPreDiagnostico.SUCESSO,
                new BigDecimal("85.50"), new BigDecimal("72.25"), "ok", "prompt secreto", "modelo-x"));
        PreDiagnosticoResposta r = PreDiagnosticoResposta.de(p);

        assertThat(r).isEqualTo(new PreDiagnosticoResposta(StatusPreDiagnostico.SUCESSO,
                new BigDecimal("85.50"), new BigDecimal("72.25"), "ok", p.getCriadoEm()));
        assertThat(Arrays.stream(PreDiagnosticoResposta.class.getRecordComponents()).map(c -> c.getName()))
                .doesNotContain("prompt", "modelo");
    }

    @Test
    void solicitacaoDetalheDeComPreDiagnostico() {
        Solicitacao s = solicitacao();
        DisciplinaCursada d = comId(new DisciplinaCursada(s, "BD I", "UFLA", 40, "modelagem sql"));
        Anexo a = comId(new Anexo(s, "historico.pdf", "application/pdf", 10L));
        PreDiagnostico p = comId(new PreDiagnostico(s, StatusPreDiagnostico.FALHA,
                new BigDecimal("40.00"), null, "Carga abaixo", "p", "m"));

        SolicitacaoDetalhe det = SolicitacaoDetalhe.de(s, List.of(d), List.of(a), p);

        assertThat(det.id()).isEqualTo(s.getId());
        assertThat(det.alunoId()).isEqualTo(s.getAlunoId());
        assertThat(det.curso()).isEqualTo("ADS");
        assertThat(det.disciplinaDestino()).isEqualTo("Banco de Dados");
        assertThat(det.cargaHorariaDestino()).isEqualTo(60);
        assertThat(det.status()).isEqualTo(StatusSolicitacao.ENVIADA);
        assertThat(det.criadoEm()).isEqualTo(s.getCriadoEm());
        assertThat(det.atualizadoEm()).isEqualTo(s.getAtualizadoEm());
        assertThat(det.disciplinas()).containsExactly(DisciplinaCursadaResposta.de(d));
        assertThat(det.anexos()).containsExactly(AnexoResposta.de(a));
        assertThat(det.preDiagnostico()).isEqualTo(PreDiagnosticoResposta.de(p));
        assertThat(det.preDiagnostico().percentualConteudo()).isNull();
    }

    @Test
    void solicitacaoDetalheDeSemPreDiagnostico() {
        SolicitacaoDetalhe det = SolicitacaoDetalhe.de(solicitacao(), List.of(), List.of(), null);

        assertThat(det.preDiagnostico()).isNull();
        assertThat(det.disciplinas()).isEmpty();
        assertThat(det.anexos()).isEmpty();
    }

    // ---------- Validacao ----------

    private static Set<String> camposInvalidos(Object dto) {
        Set<ConstraintViolation<Object>> v = validator.validate(dto);
        return v.stream().map(c -> c.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void criarSolicitacaoValidaNaoGeraViolacoes() {
        CriarSolicitacao ok = new CriarSolicitacao(UUID.randomUUID(), UUID.randomUUID(),
                List.of(new DisciplinaCursadaEntrada("BD I", "UFLA", 40, "modelagem sql")));
        assertThat(validator.validate(ok)).isEmpty();
    }

    @Test
    void criarSolicitacaoInvalidaGeraViolacoes() {
        CriarSolicitacao vazia = new CriarSolicitacao(null, null, List.of());
        assertThat(camposInvalidos(vazia)).containsExactlyInAnyOrder("alunoId", "disciplinaDestinoId", "disciplinas");

        CriarSolicitacao semLista = new CriarSolicitacao(UUID.randomUUID(), UUID.randomUUID(), null);
        assertThat(camposInvalidos(semLista)).containsExactlyInAnyOrder("disciplinas");
    }

    @Test
    void criarSolicitacaoValidaDisciplinasAninhadas() {
        CriarSolicitacao dto = new CriarSolicitacao(UUID.randomUUID(), UUID.randomUUID(),
                List.of(new DisciplinaCursadaEntrada("BD I", "UFLA", 40, "modelagem sql"),
                        new DisciplinaCursadaEntrada("", null, -1, " ")));
        assertThat(camposInvalidos(dto)).containsExactlyInAnyOrder(
                "disciplinas[1].nome", "disciplinas[1].instituicao", "disciplinas[1].cargaHoraria",
                "disciplinas[1].ementa");
    }

    @Test
    void acaoRequestLimitaObservacaoA2000() {
        assertThat(validator.validate(new AcaoRequest(null))).isEmpty();
        assertThat(validator.validate(new AcaoRequest("x".repeat(2000)))).isEmpty();
        assertThat(camposInvalidos(new AcaoRequest("x".repeat(2001)))).containsExactly("observacao");
    }

    @Test
    void anexoDoAlunoNaoTrazMotivoNemTipoMasOutrosPerfisTrazem() {
        Solicitacao s = solicitacao();
        Anexo a = comId(new Anexo(s, "ementa.pdf", "application/pdf", 10L));
        a.registrarVerificacao(StatusVerificacao.INVALIDO, TipoDetectado.OUTRO, "Documento sem relacao", "m");

        AnexoResposta aluno = AnexoResposta.de(a, false);
        AnexoResposta equipe = AnexoResposta.de(a, true);

        assertThat(aluno.statusVerificacao()).isEqualTo(StatusVerificacao.INVALIDO);
        assertThat(aluno.motivoVerificacao()).isNull();
        assertThat(aluno.tipoDetectado()).isNull();
        assertThat(equipe.motivoVerificacao()).isEqualTo("Documento sem relacao");
        assertThat(equipe.tipoDetectado()).isEqualTo(TipoDetectado.OUTRO);

        assertThat(SolicitacaoDetalhe.de(s, List.of(), List.of(a), null, false, false).anexos().get(0)
                .motivoVerificacao()).isNull();
        assertThat(SolicitacaoDetalhe.de(s, List.of(), List.of(a), null, false, true).anexos().get(0)
                .motivoVerificacao()).isEqualTo("Documento sem relacao");
    }
}
