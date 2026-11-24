package ifsuldeminas.edu.br.ifsystem.aproveitamento.api;

import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.AproveitamentoDtos.HistoricoItem;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.AproveitamentoDtos.SolicitacaoDetalhe;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.Anexo;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.AnexoConteudo;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.ParecerIa;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.RecomendacaoIa;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.Solicitacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.StatusParecerIa;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.StatusSolicitacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.service.AnexoService;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.service.FluxoStatusService;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.service.ParecerIaService;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.service.SolicitacaoService;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.service.VerificacaoAnexoService;
import ifsuldeminas.edu.br.ifsystem.auth.AuthTestSupport;
import ifsuldeminas.edu.br.ifsystem.auth.config.SecurityConfig;
import ifsuldeminas.edu.br.ifsystem.auth.service.JwtService;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.usuario.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AproveitamentoController.class)
@Import({SecurityConfig.class, JwtService.class})
class AproveitamentoControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private SolicitacaoService solicitacoes;
    @MockitoBean
    private FluxoStatusService fluxo;
    @MockitoBean
    private AnexoService anexoService;
    @MockitoBean
    private ParecerIaService parecerIa;
    @MockitoBean
    private VerificacaoAnexoService verificacao;
    // JwtService real (gera os tokens dos testes); o filtro JWT consulta o usuario no UsuarioService.
    @Autowired
    private JwtService jwtService;
    @MockitoBean
    private UsuarioService usuarioService;

    private final UUID aluno = UUID.randomUUID();
    private final UUID outroAluno = UUID.randomUUID();
    private final UUID servidor = UUID.randomUUID();
    private final UUID solicitacaoId = UUID.randomUUID();

    // ---------- helpers ----------

    private Solicitacao solicitacao(UUID alunoId) {
        Solicitacao s = new Solicitacao(alunoId, "ADS", "Banco de Dados", 60);
        ReflectionTestUtils.setField(s, "id", solicitacaoId);
        return s;
    }

    /** Autentica a requisicao como o usuario informado, com um JWT real. */
    private <T extends AbstractMockHttpServletRequestBuilder<T>> T como(T req, UUID id, Perfil perfil) {
        when(usuarioService.buscarAtivo(id)).thenReturn(Optional.of(AuthTestSupport.usuario(id, perfil)));
        return req.header("Authorization", AuthTestSupport.bearer(jwtService, id, perfil));
    }

    private String corpoCriar(UUID alunoId) {
        return """
                {"alunoId":"%s","disciplinaDestinoId":"%s",
                 "disciplinas":[{"nome":"BD I","instituicao":"UFLA","cargaHoraria":40,"ementa":"modelagem sql"}]}
                """.formatted(alunoId, UUID.randomUUID());
    }

    private MockHttpServletRequestBuilder postCriar(String corpo) {
        return post("/aproveitamentos").contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    // ---------- POST criar ----------

    @Test
    void criarRetorna201ComLocation() throws Exception {
        when(solicitacoes.criar(any())).thenReturn(solicitacao(aluno));

        mvc.perform(como(postCriar(corpoCriar(aluno)), aluno, Perfil.ALUNO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/aproveitamentos/" + solicitacaoId))
                .andExpect(jsonPath("$.id").value(solicitacaoId.toString()))
                .andExpect(jsonPath("$.alunoId").value(aluno.toString()))
                .andExpect(jsonPath("$.status").value("RASCUNHO"));
    }

    @Test
    void criarComBodyInvalidoRetorna400() throws Exception {
        String invalido = """
                {"alunoId":"%s","disciplinas":[]}
                """.formatted(aluno);

        mvc.perform(como(postCriar(invalido), aluno, Perfil.ALUNO))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(solicitacoes);
    }

    @Test
    void criarSemTokenRetorna401() throws Exception {
        mvc.perform(postCriar(corpoCriar(aluno)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(solicitacoes);
    }

    @Test
    void criarComPerfilDiferenteDeAlunoRetorna403() throws Exception {
        mvc.perform(como(postCriar(corpoCriar(servidor)), servidor, Perfil.SERVIDOR))
                .andExpect(status().isForbidden());
        verify(solicitacoes, never()).criar(any());
    }

    @Test
    void criarComAlunoIdDiferenteDoTokenRetorna403() throws Exception {
        mvc.perform(como(postCriar(corpoCriar(outroAluno)), aluno, Perfil.ALUNO))
                .andExpect(status().isForbidden());
        verify(solicitacoes, never()).criar(any());
    }

    // ---------- GET por id ----------

    @Test
    void detalharRetorna200() throws Exception {
        Solicitacao s = solicitacao(aluno);
        when(solicitacoes.buscar(solicitacaoId)).thenReturn(s);
        when(solicitacoes.detalhar(eq(solicitacaoId), any(), any()))
                .thenReturn(SolicitacaoDetalhe.de(s, List.of(), List.of(), null));

        mvc.perform(como(get("/aproveitamentos/{id}", solicitacaoId), aluno, Perfil.ALUNO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(solicitacaoId.toString()))
                .andExpect(jsonPath("$.cargaHorariaDestino").value(60))
                .andExpect(jsonPath("$.preDiagnostico").doesNotExist());
    }

    @Test
    void detalharSolicitacaoDeOutroAlunoRetorna403() throws Exception {
        when(solicitacoes.buscar(solicitacaoId)).thenReturn(solicitacao(outroAluno));

        mvc.perform(como(get("/aproveitamentos/{id}", solicitacaoId), aluno, Perfil.ALUNO))
                .andExpect(status().isForbidden());
        verify(solicitacoes, never()).detalhar(any(), any(), any());
    }

    @Test
    void detalharPermiteServidorVerSolicitacaoDeQualquerAluno() throws Exception {
        Solicitacao s = solicitacao(outroAluno);
        when(solicitacoes.buscar(solicitacaoId)).thenReturn(s);
        when(solicitacoes.detalhar(eq(solicitacaoId), any(), any()))
                .thenReturn(SolicitacaoDetalhe.de(s, List.of(), List.of(), null));

        mvc.perform(como(get("/aproveitamentos/{id}", solicitacaoId), servidor, Perfil.SERVIDOR))
                .andExpect(status().isOk());
    }

    @Test
    void detalharInexistenteRetorna404() throws Exception {
        when(solicitacoes.buscar(solicitacaoId))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitacao nao encontrada"));

        mvc.perform(como(get("/aproveitamentos/{id}", solicitacaoId), aluno, Perfil.ALUNO))
                .andExpect(status().isNotFound());
    }

    @Test
    void detalharExpoePodeDecidirDoProfessor() throws Exception {
        UUID professor = UUID.randomUUID();
        Solicitacao s = solicitacao(aluno);
        when(solicitacoes.buscar(solicitacaoId)).thenReturn(s);
        when(solicitacoes.detalhar(solicitacaoId, professor, Perfil.PROFESSOR))
                .thenReturn(SolicitacaoDetalhe.de(s, List.of(), List.of(), null, true));

        mvc.perform(como(get("/aproveitamentos/{id}", solicitacaoId), professor, Perfil.PROFESSOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podeDecidir").value(true));
    }

    @Test
    void detalharPodeDecidirFalsoParaProfessorNaoCoordenador() throws Exception {
        UUID professor = UUID.randomUUID();
        Solicitacao s = solicitacao(aluno);
        when(solicitacoes.buscar(solicitacaoId)).thenReturn(s);
        when(solicitacoes.detalhar(solicitacaoId, professor, Perfil.PROFESSOR))
                .thenReturn(SolicitacaoDetalhe.de(s, List.of(), List.of(), null, false));

        mvc.perform(como(get("/aproveitamentos/{id}", solicitacaoId), professor, Perfil.PROFESSOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podeDecidir").value(false));
    }

    // ---------- PUT atualizar ----------

    private MockHttpServletRequestBuilder putAtualizar() {
        return put("/aproveitamentos/{id}", solicitacaoId).contentType(MediaType.APPLICATION_JSON).content("""
                {"disciplinaDestinoId":"%s",
                 "disciplinas":[{"nome":"BD I","instituicao":"UFLA","cargaHoraria":40,"ementa":"modelagem sql"}]}
                """.formatted(UUID.randomUUID()));
    }

    @Test
    void atualizarPeloAlunoDonoRetorna200() throws Exception {
        Solicitacao s = solicitacao(aluno);
        when(solicitacoes.buscar(solicitacaoId)).thenReturn(s);
        when(solicitacoes.atualizar(eq(solicitacaoId), any())).thenReturn(s);

        mvc.perform(como(putAtualizar(), aluno, Perfil.ALUNO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(solicitacaoId.toString()));
        verify(solicitacoes).atualizar(eq(solicitacaoId), any());
    }

    @Test
    void atualizarDeOutroAlunoRetorna403() throws Exception {
        when(solicitacoes.buscar(solicitacaoId)).thenReturn(solicitacao(outroAluno));

        mvc.perform(como(putAtualizar(), aluno, Perfil.ALUNO)).andExpect(status().isForbidden());
        verify(solicitacoes, never()).atualizar(any(), any());
    }

    @Test
    void atualizarPorServidorRetorna403() throws Exception {
        mvc.perform(como(putAtualizar(), servidor, Perfil.SERVIDOR)).andExpect(status().isForbidden());
        verify(solicitacoes, never()).atualizar(any(), any());
    }

    @Test
    void atualizarSolicitacaoEnviadaRetorna409() throws Exception {
        when(solicitacoes.buscar(solicitacaoId)).thenReturn(solicitacao(aluno));
        when(solicitacoes.atualizar(eq(solicitacaoId), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Solicitacao so pode ser editada"));

        mvc.perform(como(putAtualizar(), aluno, Perfil.ALUNO)).andExpect(status().isConflict());
    }

    @Test
    void atualizarSemTokenRetorna401() throws Exception {
        mvc.perform(putAtualizar()).andExpect(status().isUnauthorized());
    }

    // ---------- GET lista ----------

    @Test
    void listarForcaAlunoIdParaOProprioAluno() throws Exception {
        when(solicitacoes.listar(any(), any(), any(), any())).thenReturn(List.of());

        mvc.perform(como(get("/aproveitamentos")
                        .param("alunoId", outroAluno.toString())
                        .param("status", "ENVIADA"), aluno, Perfil.ALUNO))
                .andExpect(status().isOk());

        verify(solicitacoes).listar(StatusSolicitacao.ENVIADA, aluno, null, null);
        verify(solicitacoes, never()).listar(any(), eq(outroAluno), any(), any());
    }

    @Test
    void listarMantemFiltroEnviadoParaServidor() throws Exception {
        Solicitacao s = solicitacao(outroAluno);
        when(solicitacoes.listar(StatusSolicitacao.CONFERENCIA, outroAluno, null, null)).thenReturn(List.of(
                AproveitamentoDtos.SolicitacaoResumo.de(s)));

        mvc.perform(como(get("/aproveitamentos")
                        .param("alunoId", outroAluno.toString())
                        .param("status", "CONFERENCIA"), servidor, Perfil.SERVIDOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(solicitacaoId.toString()));

        verify(solicitacoes).listar(StatusSolicitacao.CONFERENCIA, outroAluno, null, null);
    }

    @Test
    void listarSemFiltrosParaServidorNaoRestringeAluno() throws Exception {
        when(solicitacoes.listar(null, null, null, null)).thenReturn(List.of());

        mvc.perform(como(get("/aproveitamentos"), servidor, Perfil.SERVIDOR))
                .andExpect(status().isOk());

        verify(solicitacoes).listar(null, null, null, null);
    }

    @Test
    void listarParaProfessorRestringeAosCursosQueCoordena() throws Exception {
        UUID professor = UUID.randomUUID();
        when(solicitacoes.listar(any(), any(), any(), any())).thenReturn(List.of());

        mvc.perform(como(get("/aproveitamentos").param("alunoId", outroAluno.toString()), professor,
                        Perfil.PROFESSOR))
                .andExpect(status().isOk());

        verify(solicitacoes).listar(null, outroAluno, null, professor);
    }

    @Test
    void listarRepassaFiltroDeCurso() throws Exception {
        UUID curso = UUID.randomUUID();
        when(solicitacoes.listar(any(), any(), any(), any())).thenReturn(List.of());

        mvc.perform(como(get("/aproveitamentos").param("cursoId", curso.toString()), servidor, Perfil.SERVIDOR))
                .andExpect(status().isOk());

        verify(solicitacoes).listar(null, null, curso, null);
    }

    // ---------- GET historico ----------

    @Test
    void historicoRetorna200() throws Exception {
        when(solicitacoes.buscar(solicitacaoId)).thenReturn(solicitacao(aluno));
        when(solicitacoes.historico(solicitacaoId)).thenReturn(List.of(
                new HistoricoItem(null, StatusSolicitacao.RASCUNHO, aluno, null, OffsetDateTime.now())));

        mvc.perform(como(get("/aproveitamentos/{id}/historico", solicitacaoId), aluno, Perfil.ALUNO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].statusNovo").value("RASCUNHO"))
                .andExpect(jsonPath("$[0].responsavelId").value(aluno.toString()));
    }

    @Test
    void historicoDeOutroAlunoRetorna403() throws Exception {
        when(solicitacoes.buscar(solicitacaoId)).thenReturn(solicitacao(outroAluno));

        mvc.perform(como(get("/aproveitamentos/{id}/historico", solicitacaoId), aluno, Perfil.ALUNO))
                .andExpect(status().isForbidden());
        verify(solicitacoes, never()).historico(any());
    }

    // ---------- POST acao ----------

    private MockHttpServletRequestBuilder postAcao(String acao, String corpo) {
        MockHttpServletRequestBuilder req = post("/aproveitamentos/{id}/{acao}", solicitacaoId, acao);
        return corpo == null ? req : req.contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    @Test
    void acaoDelegaOsArgumentosAoFluxo() throws Exception {
        Solicitacao s = solicitacao(aluno);
        s.alterarStatus(StatusSolicitacao.ENVIADA);
        when(fluxo.executar(solicitacaoId, "enviar", aluno, Perfil.ALUNO, "pronto")).thenReturn(s);

        mvc.perform(como(postAcao("enviar", "{\"observacao\":\"pronto\"}"), aluno, Perfil.ALUNO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(solicitacaoId.toString()))
                .andExpect(jsonPath("$.status").value("ENVIADA"));

        verify(fluxo).executar(solicitacaoId, "enviar", aluno, Perfil.ALUNO, "pronto");
    }

    @Test
    void acaoSemBodyEnviaObservacaoNula() throws Exception {
        when(fluxo.executar(solicitacaoId, "conferir", servidor, Perfil.SERVIDOR, null))
                .thenReturn(solicitacao(aluno));

        mvc.perform(como(postAcao("conferir", null), servidor, Perfil.SERVIDOR))
                .andExpect(status().isOk());

        verify(fluxo).executar(solicitacaoId, "conferir", servidor, Perfil.SERVIDOR, null);
    }

    @Test
    void acaoPropagaErrosDoFluxo() throws Exception {
        for (HttpStatus erro : List.of(HttpStatus.BAD_REQUEST, HttpStatus.FORBIDDEN, HttpStatus.CONFLICT)) {
            doThrow(new ResponseStatusException(erro, "erro do fluxo"))
                    .when(fluxo).executar(any(), any(), any(), any(), any());

            mvc.perform(como(postAcao("enviar", null), aluno, Perfil.ALUNO))
                    .andExpect(status().is(erro.value()));
        }
    }

    @Test
    void acaoComObservacaoMuitoLongaRetorna400() throws Exception {
        mvc.perform(como(postAcao("enviar", "{\"observacao\":\"" + "x".repeat(2001) + "\"}"), aluno, Perfil.ALUNO))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(fluxo);
    }

    @Test
    void acaoSemTokenRetorna401() throws Exception {
        mvc.perform(postAcao("enviar", null))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(fluxo);
    }

    // ---------- anexos ----------

    private final UUID anexoId = UUID.randomUUID();
    private final byte[] pdfBytes = "%PDF-1.7\nconteudo".getBytes(StandardCharsets.US_ASCII);

    private Anexo anexo(Solicitacao s) {
        Anexo a = new Anexo(s, "historico.pdf", "application/pdf", (long) pdfBytes.length);
        ReflectionTestUtils.setField(a, "id", anexoId);
        return a;
    }

    private void solicitacaoDoAluno(UUID dono) {
        when(solicitacoes.buscar(solicitacaoId)).thenReturn(solicitacao(dono));
    }

    private MockMultipartHttpServletRequestBuilder postAnexo() {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "historico.pdf", "application/pdf", pdfBytes);
        return multipart("/aproveitamentos/{id}/anexos", solicitacaoId).file(arquivo);
    }

    private MockHttpServletRequestBuilder getAnexo() {
        return get("/aproveitamentos/{id}/anexos/{anexoId}", solicitacaoId, anexoId);
    }

    private MockHttpServletRequestBuilder deleteAnexo() {
        return delete("/aproveitamentos/{id}/anexos/{anexoId}", solicitacaoId, anexoId);
    }

    // POST anexos

    @Test
    void anexarRetorna201ComLocation() throws Exception {
        Solicitacao s = solicitacao(aluno);
        solicitacaoDoAluno(aluno);
        when(anexoService.anexar(eq(solicitacaoId), any(MultipartFile.class))).thenReturn(anexo(s));

        mvc.perform(como(postAnexo(), aluno, Perfil.ALUNO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/aproveitamentos/" + solicitacaoId + "/anexos/" + anexoId))
                .andExpect(jsonPath("$.id").value(anexoId.toString()))
                .andExpect(jsonPath("$.nomeArquivo").value("historico.pdf"))
                .andExpect(jsonPath("$.tamanho").value(pdfBytes.length));

        verify(anexoService).anexar(eq(solicitacaoId), any(MultipartFile.class));
    }

    @Test
    void anexarSemTokenRetorna401() throws Exception {
        mvc.perform(postAnexo())
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(anexoService);
    }

    @ParameterizedTest
    @EnumSource(value = Perfil.class, names = {"SERVIDOR", "PROFESSOR", "ADMINISTRADOR"})
    void anexarComPerfilDiferenteDeAlunoRetorna403(Perfil perfil) throws Exception {
        mvc.perform(como(postAnexo(), servidor, perfil))
                .andExpect(status().isForbidden());
        verify(anexoService, never()).anexar(any(), any());
    }

    @Test
    void anexarEmSolicitacaoDeOutroAlunoRetorna403() throws Exception {
        solicitacaoDoAluno(outroAluno);

        mvc.perform(como(postAnexo(), aluno, Perfil.ALUNO))
                .andExpect(status().isForbidden());
        verify(anexoService, never()).anexar(any(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 409, 415})
    void anexarPropagaErrosDoServico(int codigo) throws Exception {
        solicitacaoDoAluno(aluno);
        doThrow(new ResponseStatusException(HttpStatus.valueOf(codigo), "erro do servico"))
                .when(anexoService).anexar(eq(solicitacaoId), any(MultipartFile.class));

        mvc.perform(como(postAnexo(), aluno, Perfil.ALUNO))
                .andExpect(status().is(codigo));
    }

    @Test
    void anexarSolicitacaoInexistenteRetorna404() throws Exception {
        when(solicitacoes.buscar(solicitacaoId))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitacao nao encontrada"));

        mvc.perform(como(postAnexo(), aluno, Perfil.ALUNO))
                .andExpect(status().isNotFound());
        verify(anexoService, never()).anexar(any(), any());
    }

    @Test
    void anexarAcimaDoLimiteRetorna413() throws Exception {
        solicitacaoDoAluno(aluno);
        doThrow(new MaxUploadSizeExceededException(5L * 1024 * 1024))
                .when(anexoService).anexar(eq(solicitacaoId), any(MultipartFile.class));

        mvc.perform(como(postAnexo(), aluno, Perfil.ALUNO))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.erro").exists());
    }

    // GET download

    @Test
    void baixarRetorna200ComTokenEBytes() throws Exception {
        Solicitacao s = solicitacao(aluno);
        solicitacaoDoAluno(aluno);
        when(anexoService.baixar(solicitacaoId, anexoId)).thenReturn(new AnexoConteudo(anexo(s), pdfBytes));

        mvc.perform(como(getAnexo(), aluno, Perfil.ALUNO))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"historico.pdf\""))
                .andExpect(content().bytes(pdfBytes));
    }

    @ParameterizedTest
    @EnumSource(value = Perfil.class, names = {"SERVIDOR", "PROFESSOR"})
    void baixarPermiteServidorEProfessor(Perfil perfil) throws Exception {
        Solicitacao s = solicitacao(outroAluno);
        when(anexoService.baixar(solicitacaoId, anexoId)).thenReturn(new AnexoConteudo(anexo(s), pdfBytes));

        mvc.perform(como(getAnexo(), servidor, perfil))
                .andExpect(status().isOk())
                .andExpect(content().bytes(pdfBytes));
    }

    @Test
    void baixarComAdministradorRetorna403() throws Exception {
        mvc.perform(como(getAnexo(), servidor, Perfil.ADMINISTRADOR))
                .andExpect(status().isForbidden());
        verify(anexoService, never()).baixar(any(), any());
    }

    @Test
    void baixarAnexoDeOutroAlunoRetorna403() throws Exception {
        solicitacaoDoAluno(outroAluno);

        mvc.perform(como(getAnexo(), aluno, Perfil.ALUNO))
                .andExpect(status().isForbidden());
        verify(anexoService, never()).baixar(any(), any());
    }

    @Test
    void baixarSemTokenRetorna401() throws Exception {
        mvc.perform(getAnexo())
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(anexoService);
    }

    @Test
    void baixarPropagaNaoEncontradoDoServico() throws Exception {
        when(anexoService.baixar(solicitacaoId, anexoId))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Anexo nao encontrado"));

        mvc.perform(como(getAnexo(), servidor, Perfil.SERVIDOR))
                .andExpect(status().isNotFound());
    }

    // DELETE anexos

    @Test
    void removerRetorna204ParaAlunoDono() throws Exception {
        solicitacaoDoAluno(aluno);

        mvc.perform(como(deleteAnexo(), aluno, Perfil.ALUNO))
                .andExpect(status().isNoContent());
        verify(anexoService).remover(solicitacaoId, anexoId);
    }

    @Test
    void removerComServidorRetorna403() throws Exception {
        mvc.perform(como(deleteAnexo(), servidor, Perfil.SERVIDOR))
                .andExpect(status().isForbidden());
        verify(anexoService, never()).remover(any(), any());
    }

    @Test
    void removerAnexoDeOutroAlunoRetorna403() throws Exception {
        solicitacaoDoAluno(outroAluno);

        mvc.perform(como(deleteAnexo(), aluno, Perfil.ALUNO))
                .andExpect(status().isForbidden());
        verify(anexoService, never()).remover(any(), any());
    }

    @Test
    void removerPropagaConflitoDoServico() throws Exception {
        solicitacaoDoAluno(aluno);
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Status nao permite remover"))
                .when(anexoService).remover(solicitacaoId, anexoId);

        mvc.perform(como(deleteAnexo(), aluno, Perfil.ALUNO))
                .andExpect(status().isConflict());
    }

    // ---------- parecer da IA ----------

    private ParecerIa parecer() {
        return new ParecerIa(solicitacao(aluno), StatusParecerIa.CONCLUIDO, RecomendacaoIa.INCONCLUSIVO,
                50, "Analise simulada", "fake", null);
    }

    @Test
    void parecerIaRetorna200ParaServidor() throws Exception {
        when(solicitacoes.buscar(solicitacaoId)).thenReturn(solicitacao(aluno));
        when(parecerIa.maisRecente(solicitacaoId)).thenReturn(Optional.of(parecer()));

        mvc.perform(como(get("/aproveitamentos/{id}/parecer-ia", solicitacaoId), servidor, Perfil.SERVIDOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.recomendacao").value("INCONCLUSIVO"))
                .andExpect(jsonPath("$.similaridade").value(50))
                .andExpect(jsonPath("$.modelo").value("fake"));
    }

    @Test
    void parecerIaRetorna403ParaAlunoMesmoSendoDono() throws Exception {
        mvc.perform(como(get("/aproveitamentos/{id}/parecer-ia", solicitacaoId), aluno, Perfil.ALUNO))
                .andExpect(status().isForbidden());
        verifyNoInteractions(parecerIa);
    }

    @Test
    void parecerIaRetorna404SemParecer() throws Exception {
        when(solicitacoes.buscar(solicitacaoId)).thenReturn(solicitacao(aluno));
        when(parecerIa.maisRecente(solicitacaoId)).thenReturn(Optional.empty());

        mvc.perform(como(get("/aproveitamentos/{id}/parecer-ia", solicitacaoId), servidor, Perfil.SERVIDOR))
                .andExpect(status().isNotFound());
    }

    @Test
    void regerarParecerIaRetorna202QuandoPermitido() throws Exception {
        when(solicitacoes.buscar(solicitacaoId)).thenReturn(solicitacao(aluno));
        when(parecerIa.podeRegerar(servidor, Perfil.SERVIDOR, solicitacaoId)).thenReturn(true);

        mvc.perform(como(post("/aproveitamentos/{id}/parecer-ia/regerar", solicitacaoId), servidor, Perfil.SERVIDOR))
                .andExpect(status().isAccepted());
        verify(parecerIa).regerar(solicitacaoId);
    }

    @Test
    void regerarParecerIaRetorna403SemPermissao() throws Exception {
        when(solicitacoes.buscar(solicitacaoId)).thenReturn(solicitacao(aluno));
        when(parecerIa.podeRegerar(aluno, Perfil.ALUNO, solicitacaoId)).thenReturn(false);

        mvc.perform(como(post("/aproveitamentos/{id}/parecer-ia/regerar", solicitacaoId), aluno, Perfil.ALUNO))
                .andExpect(status().isForbidden());
        verify(parecerIa, never()).regerar(any());
    }

    // ---------- reverificar anexo ----------

    @Test
    void reverificarAnexoRetorna202ParaServidorEMostraOMotivo() throws Exception {
        UUID anexoId = UUID.randomUUID();
        UUID professor = UUID.randomUUID();
        Solicitacao s = solicitacao(aluno);
        Anexo anexo = new Anexo(s, "ementa.pdf", "application/pdf", 10L);
        when(solicitacoes.buscar(solicitacaoId)).thenReturn(s);
        when(verificacao.podeReverificar(servidor, Perfil.SERVIDOR, solicitacaoId)).thenReturn(true);
        when(verificacao.reverificar(solicitacaoId, anexoId)).thenReturn(anexo);

        mvc.perform(como(post("/aproveitamentos/{id}/anexos/{anexo}/reverificar", solicitacaoId, anexoId),
                        servidor, Perfil.SERVIDOR))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.statusVerificacao").value("VERIFICANDO"));
        verify(verificacao).reverificar(solicitacaoId, anexoId);
    }

    @Test
    void reverificarAnexoRetorna202ParaProfessorCoordenador() throws Exception {
        UUID anexoId = UUID.randomUUID();
        UUID professor = UUID.randomUUID();
        Solicitacao s = solicitacao(aluno);
        when(solicitacoes.buscar(solicitacaoId)).thenReturn(s);
        when(verificacao.podeReverificar(professor, Perfil.PROFESSOR, solicitacaoId)).thenReturn(true);
        when(verificacao.reverificar(solicitacaoId, anexoId))
                .thenReturn(new Anexo(s, "ementa.pdf", "application/pdf", 10L));

        mvc.perform(como(post("/aproveitamentos/{id}/anexos/{anexo}/reverificar", solicitacaoId, anexoId),
                        professor, Perfil.PROFESSOR))
                .andExpect(status().isAccepted());
    }

    @Test
    void reverificarAnexoRetorna403ParaAlunoEProfessorSemPermissao() throws Exception {
        UUID anexoId = UUID.randomUUID();
        UUID professor = UUID.randomUUID();
        when(solicitacoes.buscar(solicitacaoId)).thenReturn(solicitacao(aluno));
        when(verificacao.podeReverificar(aluno, Perfil.ALUNO, solicitacaoId)).thenReturn(false);
        when(verificacao.podeReverificar(professor, Perfil.PROFESSOR, solicitacaoId)).thenReturn(false);

        mvc.perform(como(post("/aproveitamentos/{id}/anexos/{anexo}/reverificar", solicitacaoId, anexoId),
                        aluno, Perfil.ALUNO)).andExpect(status().isForbidden());
        mvc.perform(como(post("/aproveitamentos/{id}/anexos/{anexo}/reverificar", solicitacaoId, anexoId),
                        professor, Perfil.PROFESSOR)).andExpect(status().isForbidden());
        verify(verificacao, never()).reverificar(any(), any());
    }

    @Test
    void reverificarAnexoInexistenteRetorna404() throws Exception {
        UUID anexoId = UUID.randomUUID();
        UUID professor = UUID.randomUUID();
        when(solicitacoes.buscar(solicitacaoId)).thenReturn(solicitacao(aluno));
        when(verificacao.podeReverificar(servidor, Perfil.SERVIDOR, solicitacaoId)).thenReturn(true);
        when(verificacao.reverificar(solicitacaoId, anexoId))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Anexo nao encontrado"));

        mvc.perform(como(post("/aproveitamentos/{id}/anexos/{anexo}/reverificar", solicitacaoId, anexoId),
                        servidor, Perfil.SERVIDOR)).andExpect(status().isNotFound());
    }

    // ---------- POST nova-tentativa ----------

    @Test
    void novaTentativaRetorna201() throws Exception {
        UUID novaId = UUID.randomUUID();
        Solicitacao nova = new Solicitacao(aluno, "ADS", "Banco de Dados", 60);
        ReflectionTestUtils.setField(nova, "id", novaId);
        when(solicitacoes.novaTentativa(solicitacaoId, aluno)).thenReturn(nova);

        mvc.perform(como(post("/aproveitamentos/{id}/nova-tentativa", solicitacaoId), aluno, Perfil.ALUNO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/aproveitamentos/" + novaId))
                .andExpect(jsonPath("$.id").value(novaId.toString()))
                .andExpect(jsonPath("$.status").value("RASCUNHO"));
        verify(fluxo, never()).executar(any(), any(), any(), any(), any());
    }

    @Test
    void novaTentativaDeOutroAlunoRetorna403() throws Exception {
        when(solicitacoes.novaTentativa(solicitacaoId, aluno))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        mvc.perform(como(post("/aproveitamentos/{id}/nova-tentativa", solicitacaoId), aluno, Perfil.ALUNO))
                .andExpect(status().isForbidden());
    }

    @Test
    void novaTentativaPorServidorRetorna403() throws Exception {
        mvc.perform(como(post("/aproveitamentos/{id}/nova-tentativa", solicitacaoId), servidor, Perfil.SERVIDOR))
                .andExpect(status().isForbidden());
        verify(solicitacoes, never()).novaTentativa(any(), any());
    }

    @Test
    void novaTentativaForaDeIndeferidaRetorna409() throws Exception {
        when(solicitacoes.novaTentativa(solicitacaoId, aluno))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT));

        mvc.perform(como(post("/aproveitamentos/{id}/nova-tentativa", solicitacaoId), aluno, Perfil.ALUNO))
                .andExpect(status().isConflict());
    }

    @Test
    void segundaNovaTentativaRetorna409ComIdDaExistente() throws Exception {
        UUID existente = UUID.randomUUID();
        when(solicitacoes.novaTentativa(solicitacaoId, aluno))
                .thenThrow(new ifsuldeminas.edu.br.ifsystem.aproveitamento.service.NovaTentativaJaExisteException(existente));

        mvc.perform(como(post("/aproveitamentos/{id}/nova-tentativa", solicitacaoId), aluno, Perfil.ALUNO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Já existe uma nova tentativa para esta solicitação"))
                .andExpect(jsonPath("$.novaTentativaId").value(existente.toString()));
    }
}
