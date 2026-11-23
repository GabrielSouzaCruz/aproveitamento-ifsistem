package ifsuldeminas.edu.br.ifsystem.aproveitamento.service;

import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.AproveitamentoDtos.CriarSolicitacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.AproveitamentoDtos.DisciplinaCursadaEntrada;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.Anexo;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.Coordenador;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.Curso;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.DisciplinaCurso;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.Solicitacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.StatusVerificacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.TipoDetectado;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.AnexoRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.CoordenadorRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.CursoRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.DisciplinaCursoRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.service.VerificadorAnexo.ResultadoVerificacao;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Perfil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

/** Sem @Transactional de proposito: a verificacao so roda depois do commit do upload. */
@SpringBootTest
class VerificacaoAnexoServiceTest {

    private static final Duration ESPERA = Duration.ofSeconds(10);
    private static final String EMENTA = "Modelagem de dados, modelo relacional, linguagem SQL e normalizacao";

    @Autowired
    private SolicitacaoService solicitacoes;
    @Autowired
    private AnexoService anexoService;
    @Autowired
    private VerificacaoAnexoService verificacao;
    @Autowired
    private AnexoRepository anexos;
    @Autowired
    private CursoRepository cursos;
    @Autowired
    private DisciplinaCursoRepository catalogo;
    @Autowired
    private CoordenadorRepository coordenadores;
    @MockitoSpyBean
    private VerificadorAnexo verificador;

    private Solicitacao rascunho(String disciplinaCursada, int carga) {
        return rascunho(disciplinaCursada, carga, EMENTA);
    }

    private Solicitacao rascunho(String disciplinaCursada, int carga, String ementaCursada) {
        Curso curso = cursos.save(new Curso("T" + UUID.randomUUID().toString().substring(0, 8), "Curso de Teste"));
        DisciplinaCurso destino = catalogo.save(new DisciplinaCurso(curso, "BD", "Banco de Dados", 60, EMENTA));
        return solicitacoes.criar(new CriarSolicitacao(UUID.randomUUID(), destino.getId(),
                List.of(new DisciplinaCursadaEntrada(disciplinaCursada, "UFLA", carga, ementaCursada))));
    }

    private static MockMultipartFile fixture(String nome) throws IOException {
        try (var in = VerificacaoAnexoServiceTest.class.getResourceAsStream("/pdf/" + nome)) {
            return new MockMultipartFile("arquivo", nome, "application/pdf", in.readAllBytes());
        }
    }

    private Anexo aguardarFimDaVerificacao(UUID anexoId) {
        await().atMost(ESPERA).untilAsserted(() -> assertThat(anexos.findById(anexoId))
                .hasValueSatisfying(a -> assertThat(a.getStatusVerificacao())
                        .isNotEqualTo(StatusVerificacao.VERIFICANDO)));
        return anexos.findById(anexoId).orElseThrow();
    }

    @Test
    void uploadExtraiOTextoEVerificaEmSegundoPlano() throws IOException {
        Solicitacao s = rascunho("Banco de Dados I", 60);

        Anexo anexo = anexoService.anexar(s.getId(), fixture("pdf-com-texto.pdf"));

        assertThat(anexo.getStatusVerificacao()).isEqualTo(StatusVerificacao.VERIFICANDO);
        assertThat(anexo.getTextoExtraido()).contains("Banco de Dados I", "60 horas");
        Anexo verificado = aguardarFimDaVerificacao(anexo.getId());
        assertThat(verificado.getStatusVerificacao()).isEqualTo(StatusVerificacao.VALIDO);
        assertThat(verificado.getTipoDetectado()).isEqualTo(TipoDetectado.EMENTA);
        assertThat(verificado.getModeloVerificacao()).isEqualTo("verificador-local-v1");
        assertThat(verificado.getVerificadoEm()).isNotNull();
        assertThat(verificado.getMotivoVerificacao()).isNotBlank();
    }

    @Test
    void pdfSemTextoFicaParaConferirNoVerificadorLocal() throws IOException {
        Solicitacao s = rascunho("Banco de Dados I", 60);

        Anexo anexo = anexoService.anexar(s.getId(), fixture("pdf-sem-texto.pdf"));

        assertThat(anexo.getTextoExtraido()).isEmpty();
        Anexo verificado = aguardarFimDaVerificacao(anexo.getId());
        assertThat(verificado.getStatusVerificacao()).isEqualTo(StatusVerificacao.PRECISA_CONFERIR);
        assertThat(verificado.getMotivoVerificacao()).isEqualTo("PDF sem texto, possivelmente escaneado");
    }

    @Test
    void documentoDeOutraDisciplinaEInvalido() throws IOException {
        Solicitacao s = rascunho("Calculo Diferencial", 90, "Limites, derivadas, integrais e series numericas");

        Anexo anexo = anexoService.anexar(s.getId(), fixture("pdf-com-texto.pdf"));

        assertThat(aguardarFimDaVerificacao(anexo.getId()).getStatusVerificacao())
                .isEqualTo(StatusVerificacao.INVALIDO);
    }

    @Test
    void transcricaoDoModeloSubstituiOTextoExtraido() throws IOException {
        doReturn(new ResultadoVerificacao(TipoDetectado.HISTORICO, StatusVerificacao.VALIDO, "ok", 60,
                "texto transcrito do PDF escaneado", "gemini-teste")).when(verificador).verificar(any());
        Solicitacao s = rascunho("Banco de Dados I", 60);

        Anexo anexo = anexoService.anexar(s.getId(), fixture("pdf-sem-texto.pdf"));

        Anexo verificado = aguardarFimDaVerificacao(anexo.getId());
        assertThat(verificado.getTextoExtraido()).isEqualTo("texto transcrito do PDF escaneado");
        assertThat(verificado.getModeloVerificacao()).isEqualTo("gemini-teste");
        assertThat(verificado.getTipoDetectado()).isEqualTo(TipoDetectado.HISTORICO);
    }

    @Test
    void falhaDoVerificadorViraPrecisaConferirSemPropagarExcecao() throws IOException {
        doThrow(new IllegalStateException("fora do ar")).when(verificador).verificar(any());
        Solicitacao s = rascunho("Banco de Dados I", 60);

        Anexo anexo = anexoService.anexar(s.getId(), fixture("pdf-com-texto.pdf"));

        Anexo verificado = aguardarFimDaVerificacao(anexo.getId());
        assertThat(verificado.getStatusVerificacao()).isEqualTo(StatusVerificacao.PRECISA_CONFERIR);
        assertThat(verificado.getMotivoVerificacao()).isEqualTo(VerificacaoAnexoService.MOTIVO_FALHA);
    }

    @Test
    void anexoInexistenteNaoLancaExcecao() {
        verificacao.verificar(UUID.randomUUID());
    }

    @Test
    void reverificarVoltaParaVerificandoEConcluiDeNovo() throws IOException {
        Solicitacao s = rascunho("Banco de Dados I", 60);
        Anexo anexo = anexoService.anexar(s.getId(), fixture("pdf-com-texto.pdf"));
        aguardarFimDaVerificacao(anexo.getId());
        doThrow(new IllegalStateException("fora do ar")).when(verificador).verificar(any());

        Anexo devolvido = verificacao.reverificar(s.getId(), anexo.getId());

        assertThat(devolvido.getStatusVerificacao()).isEqualTo(StatusVerificacao.VERIFICANDO);
        assertThat(devolvido.getMotivoVerificacao()).isNull();
        await().atMost(ESPERA).untilAsserted(() -> assertThat(anexos.findById(anexo.getId()).orElseThrow()
                .getMotivoVerificacao()).isEqualTo(VerificacaoAnexoService.MOTIVO_FALHA));
    }

    @Test
    void reverificarAnexoDeOutraSolicitacaoRetorna404() throws IOException {
        Solicitacao s = rascunho("Banco de Dados I", 60);
        Solicitacao outra = rascunho("Banco de Dados I", 60);
        Anexo anexo = anexoService.anexar(s.getId(), fixture("pdf-com-texto.pdf"));

        assertThatThrownBy(() -> verificacao.reverificar(outra.getId(), anexo.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void permissaoDeReverificarServidorECoordenadorSim_AlunoEOutroProfessorNao() {
        Solicitacao s = rascunho("Banco de Dados I", 60);
        UUID coordenador = UUID.randomUUID();
        coordenadores.save(new Coordenador(coordenador, s.getDisciplinaCursoDestino().getCurso()));

        assertThat(verificacao.podeReverificar(UUID.randomUUID(), Perfil.SERVIDOR, s.getId())).isTrue();
        assertThat(verificacao.podeReverificar(coordenador, Perfil.PROFESSOR, s.getId())).isTrue();
        assertThat(verificacao.podeReverificar(UUID.randomUUID(), Perfil.PROFESSOR, s.getId())).isFalse();
        assertThat(verificacao.podeReverificar(s.getAlunoId(), Perfil.ALUNO, s.getId())).isFalse();
        assertThat(verificacao.podeReverificar(coordenador, Perfil.PROFESSOR, UUID.randomUUID())).isFalse();
    }
}
