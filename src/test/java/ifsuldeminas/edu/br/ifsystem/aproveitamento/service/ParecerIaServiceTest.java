package ifsuldeminas.edu.br.ifsystem.aproveitamento.service;

import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.AproveitamentoDtos.CriarSolicitacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.AproveitamentoDtos.DisciplinaCursadaEntrada;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.Curso;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.DisciplinaCurso;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.ParecerIa;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.RecomendacaoIa;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.Solicitacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.StatusParecerIa;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.StatusSolicitacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.CursoRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.DisciplinaCursoRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.ParecerIaRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.SolicitacaoRepository;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Perfil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/** Sem @Transactional de proposito: o parecer so nasce depois do commit da submissao. */
@SpringBootTest
class ParecerIaServiceTest {

    private static final Duration ESPERA = Duration.ofSeconds(10);

    @Autowired
    private SolicitacaoService solicitacoes;
    @Autowired
    private FluxoStatusService fluxo;
    @Autowired
    private ParecerIaService parecerIaService;
    @Autowired
    private ParecerIaRepository pareceres;
    @Autowired
    private SolicitacaoRepository solicitacaoRepository;
    @MockitoSpyBean
    private AnalisadorEquivalencia analisador;

    @Autowired
    private CursoRepository cursos;
    @Autowired
    private DisciplinaCursoRepository catalogo;

    private static final String EMENTA = "Modelagem relacional, normalizacao e linguagem SQL";

    private Solicitacao rascunho(UUID aluno) {
        Curso curso = cursos.save(new Curso("T" + UUID.randomUUID().toString().substring(0, 8), "Curso de Teste"));
        DisciplinaCurso destino = catalogo.save(new DisciplinaCurso(curso, "BD", "Banco de Dados", 60, EMENTA));
        return solicitacoes.criar(new CriarSolicitacao(aluno, destino.getId(),
                List.of(new DisciplinaCursadaEntrada("BD I", "UFLA", 60, EMENTA))));
    }

    private ParecerIa aguardarStatus(UUID solicitacaoId, StatusParecerIa esperado) {
        await().atMost(ESPERA).untilAsserted(() -> assertThat(
                pareceres.findFirstBySolicitacaoIdOrderByGeradoEmDesc(solicitacaoId))
                .hasValueSatisfying(p -> assertThat(p.getStatus()).isEqualTo(esperado)));
        return pareceres.findFirstBySolicitacaoIdOrderByGeradoEmDesc(solicitacaoId).orElseThrow();
    }

    @Test
    void submissaoGeraParecerConcluidoComOFake() {
        UUID aluno = UUID.randomUUID();
        Solicitacao s = rascunho(aluno);

        fluxo.executar(s.getId(), "enviar", aluno, Perfil.ALUNO, null);

        ParecerIa parecer = aguardarStatus(s.getId(), StatusParecerIa.CONCLUIDO);
        assertThat(parecer.getRecomendacao()).isEqualTo(RecomendacaoIa.SUGERE_DEFERIR);
        assertThat(parecer.getSimilaridade()).isEqualTo(100);
        assertThat(parecer.getModelo()).isEqualTo("heuristica-local-v1");
        assertThat(parecer.getErro()).isNull();
        assertThat(pareceres.findAll().stream()
                .filter(p -> p.getSolicitacao().getId().equals(s.getId()))).hasSize(1);
    }

    @Test
    void falhaDoAnalisadorMarcaFalhouEMantemSolicitacaoEnviada() {
        doThrow(new IllegalStateException("modelo fora do ar")).when(analisador).analisar(any());
        UUID aluno = UUID.randomUUID();
        Solicitacao s = rascunho(aluno);

        fluxo.executar(s.getId(), "enviar", aluno, Perfil.ALUNO, null);

        ParecerIa parecer = aguardarStatus(s.getId(), StatusParecerIa.FALHOU);
        assertThat(parecer.getErro()).isEqualTo("modelo fora do ar");
        assertThat(parecer.getRecomendacao()).isNull();
        assertThat(solicitacaoRepository.findById(s.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusSolicitacao.ENVIADA);
    }

    @Test
    void regerarCriaNovoParecerSemApagarOAnterior() {
        UUID aluno = UUID.randomUUID();
        Solicitacao s = rascunho(aluno);
        fluxo.executar(s.getId(), "enviar", aluno, Perfil.ALUNO, null);
        aguardarStatus(s.getId(), StatusParecerIa.CONCLUIDO);

        parecerIaService.regerar(s.getId());

        await().atMost(ESPERA).untilAsserted(() -> assertThat(pareceres.findAll().stream()
                .filter(p -> p.getSolicitacao().getId().equals(s.getId()))
                .filter(p -> p.getStatus() == StatusParecerIa.CONCLUIDO)).hasSize(2));
    }

    @Test
    void solicitacaoInexistenteNaoLancaExcecao() {
        parecerIaService.gerar(UUID.randomUUID());
    }
}
