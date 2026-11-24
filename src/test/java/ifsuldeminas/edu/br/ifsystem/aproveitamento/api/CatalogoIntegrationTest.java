package ifsuldeminas.edu.br.ifsystem.aproveitamento.api;

import com.jayway.jsonpath.JsonPath;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.Solicitacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.DisciplinaCursadaRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.SolicitacaoRepository;
import ifsuldeminas.edu.br.ifsystem.auth.AuthTestSupport;
import ifsuldeminas.edu.br.ifsystem.auth.TestJwtDecoderConfig;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Usuario;
import ifsuldeminas.edu.br.ifsystem.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static ifsuldeminas.edu.br.ifsystem.auth.AuthTestSupport.cpfAleatorio;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Catalogo de cursos/disciplinas ponta a ponta: token do auth (decoder de teste), permissoes, banco H2 e criacao de solicitacao. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtDecoderConfig.class)
class CatalogoIntegrationTest {
    private static final String EMENTA = "Modelagem relacional, normalizacao e linguagem SQL";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private SolicitacaoRepository solicitacoes;
    @Autowired
    private DisciplinaCursadaRepository cursadas;

    private record Logado(UUID id, String bearer) {
    }

    /** Usuario "vindo do auth": o token basta; a primeira requisicao cria a copia local (upsert pelas claims). */
    private Logado novo(Perfil perfil) throws Exception {
        UUID id = UUID.randomUUID();
        String bearer = AuthTestSupport.bearer(id, perfil, cpfAleatorio());
        mvc.perform(get("/cursos").header("Authorization", bearer)).andExpect(status().isOk());
        return new Logado(id, bearer);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String corpo) {
        return b.contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    private static String codigo() {
        return "C" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String criarCurso(Logado servidor, String codigo) throws Exception {
        MvcResult r = mvc.perform(json(post("/cursos").header("Authorization", servidor.bearer()),
                        "{\"codigo\":\"%s\",\"nome\":\"Curso %s\"}".formatted(codigo, codigo)))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(), "$.id");
    }

    private static String corpoDisciplina(String codigo, int carga, String ementa) {
        return "{\"codigo\":\"%s\",\"nome\":\"Banco de Dados\",\"cargaHoraria\":%d,\"ementa\":\"%s\"}"
                .formatted(codigo, carga, ementa);
    }

    private String criarDisciplina(Logado servidor, String cursoId, String codigo) throws Exception {
        MvcResult r = mvc.perform(json(post("/cursos/{id}/disciplinas", cursoId)
                        .header("Authorization", servidor.bearer()), corpoDisciplina(codigo, 60, EMENTA)))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(), "$.id");
    }

    // ---------- cursos ----------

    @Test
    void servidorCriaAtualizaEInativaCursoEPermaneceNaListagemDele() throws Exception {
        Logado servidor = novo(Perfil.SERVIDOR);
        String cod = codigo();
        String id = criarCurso(servidor, cod);

        mvc.perform(json(put("/cursos/{id}", id).header("Authorization", servidor.bearer()),
                        "{\"codigo\":\"%s\",\"nome\":\"Nome Novo\"}".formatted(cod)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Nome Novo"));

        mvc.perform(post("/cursos/{id}/inativar", id).header("Authorization", servidor.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(false));

        mvc.perform(get("/cursos").header("Authorization", servidor.bearer()))
                .andExpect(jsonPath("$[?(@.id=='%s')].ativo".formatted(id)).value(false));
    }

    @Test
    void codigoDeCursoDuplicadoDa409() throws Exception {
        Logado servidor = novo(Perfil.SERVIDOR);
        String cod = codigo();
        criarCurso(servidor, cod);

        mvc.perform(json(post("/cursos").header("Authorization", servidor.bearer()),
                        "{\"codigo\":\"%s\",\"nome\":\"Outro\"}".formatted(cod)))
                .andExpect(status().isConflict());
    }

    @Test
    void cursoInvalidoDa400() throws Exception {
        Logado servidor = novo(Perfil.SERVIDOR);

        mvc.perform(json(post("/cursos").header("Authorization", servidor.bearer()),
                "{\"codigo\":\" \",\"nome\":\"\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void cursoInexistenteDa404() throws Exception {
        Logado servidor = novo(Perfil.SERVIDOR);

        mvc.perform(get("/cursos/{id}", UUID.randomUUID()).header("Authorization", servidor.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void semTokenDa401() throws Exception {
        mvc.perform(get("/cursos")).andExpect(status().isUnauthorized());
    }

    @Test
    void naoServidorNaoEscreveNoCatalogo() throws Exception {
        Logado servidor = novo(Perfil.SERVIDOR);
        String cursoId = criarCurso(servidor, codigo());
        String disciplinaId = criarDisciplina(servidor, cursoId, "BD1");
        String corpoCurso = "{\"codigo\":\"X1\",\"nome\":\"X\"}";

        for (Perfil perfil : new Perfil[]{Perfil.ALUNO, Perfil.PROFESSOR}) {
            String auth = novo(perfil).bearer();
            mvc.perform(json(post("/cursos").header("Authorization", auth), corpoCurso))
                    .andExpect(status().isForbidden());
            mvc.perform(json(put("/cursos/{id}", cursoId).header("Authorization", auth), corpoCurso))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/cursos/{id}/inativar", cursoId).header("Authorization", auth))
                    .andExpect(status().isForbidden());
            mvc.perform(json(post("/cursos/{id}/disciplinas", cursoId).header("Authorization", auth),
                    corpoDisciplina("D2", 60, EMENTA))).andExpect(status().isForbidden());
            mvc.perform(json(put("/cursos/{id}/disciplinas/{d}", cursoId, disciplinaId)
                    .header("Authorization", auth), corpoDisciplina("D2", 60, EMENTA)))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/cursos/{id}/disciplinas/{d}/inativar", cursoId, disciplinaId)
                    .header("Authorization", auth)).andExpect(status().isForbidden());
            mvc.perform(json(post("/cursos/{id}/coordenadores", cursoId).header("Authorization", auth),
                    "{\"professorId\":\"%s\"}".formatted(UUID.randomUUID()))).andExpect(status().isForbidden());
            mvc.perform(json(delete("/cursos/{id}/coordenadores", cursoId).header("Authorization", auth),
                    "{\"professorId\":\"%s\"}".formatted(UUID.randomUUID()))).andExpect(status().isForbidden());
        }
    }

    @Test
    void usuarioComumSoEnxergaCursosEDisciplinasAtivos() throws Exception {
        Logado servidor = novo(Perfil.SERVIDOR);
        Logado aluno = novo(Perfil.ALUNO);
        String ativo = criarCurso(servidor, codigo());
        String inativo = criarCurso(servidor, codigo());
        String disciplinaAtiva = criarDisciplina(servidor, ativo, "A1");
        String disciplinaInativa = criarDisciplina(servidor, ativo, "A2");
        mvc.perform(post("/cursos/{id}/disciplinas/{d}/inativar", ativo, disciplinaInativa)
                .header("Authorization", servidor.bearer())).andExpect(status().isOk());
        mvc.perform(post("/cursos/{id}/inativar", inativo).header("Authorization", servidor.bearer()))
                .andExpect(status().isOk());

        mvc.perform(get("/cursos").header("Authorization", aluno.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='%s')]".formatted(ativo)).isNotEmpty())
                .andExpect(jsonPath("$[?(@.id=='%s')]".formatted(inativo)).isEmpty());
        mvc.perform(get("/cursos/{id}", inativo).header("Authorization", aluno.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/cursos/{id}/disciplinas", inativo).header("Authorization", aluno.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/cursos/{id}/disciplinas", ativo).header("Authorization", aluno.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='%s')]".formatted(disciplinaAtiva)).isNotEmpty())
                .andExpect(jsonPath("$[?(@.id=='%s')]".formatted(disciplinaInativa)).isEmpty());
        mvc.perform(get("/cursos/{id}/disciplinas", ativo).header("Authorization", servidor.bearer()))
                .andExpect(jsonPath("$[?(@.id=='%s')]".formatted(disciplinaInativa)).isNotEmpty());
    }

    // ---------- disciplinas ----------

    @Test
    void servidorCriaEAtualizaDisciplinaComEmentaObrigatoria() throws Exception {
        Logado servidor = novo(Perfil.SERVIDOR);
        String cursoId = criarCurso(servidor, codigo());
        String disciplinaId = criarDisciplina(servidor, cursoId, "BD1");

        mvc.perform(json(put("/cursos/{id}/disciplinas/{d}", cursoId, disciplinaId)
                        .header("Authorization", servidor.bearer()), corpoDisciplina("BD1", 80, "Nova ementa")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cargaHoraria").value(80))
                .andExpect(jsonPath("$.ementa").value("Nova ementa"));

        mvc.perform(json(post("/cursos/{id}/disciplinas", cursoId).header("Authorization", servidor.bearer()),
                        corpoDisciplina("BD9", 60, " ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void codigoDeDisciplinaEUnicoDentroDoCursoMasPodeRepetirEmOutro() throws Exception {
        Logado servidor = novo(Perfil.SERVIDOR);
        String curso1 = criarCurso(servidor, codigo());
        String curso2 = criarCurso(servidor, codigo());
        criarDisciplina(servidor, curso1, "BD1");

        mvc.perform(json(post("/cursos/{id}/disciplinas", curso1).header("Authorization", servidor.bearer()),
                corpoDisciplina("BD1", 60, EMENTA))).andExpect(status().isConflict());
        criarDisciplina(servidor, curso2, "BD1");
    }

    @Test
    void disciplinaDeOutroCursoDa404() throws Exception {
        Logado servidor = novo(Perfil.SERVIDOR);
        String curso1 = criarCurso(servidor, codigo());
        String curso2 = criarCurso(servidor, codigo());
        String disciplinaId = criarDisciplina(servidor, curso1, "BD1");

        mvc.perform(post("/cursos/{id}/disciplinas/{d}/inativar", curso2, disciplinaId)
                .header("Authorization", servidor.bearer())).andExpect(status().isNotFound());
    }

    // ---------- coordenadores ----------

    @Test
    void servidorAdicionaERemoveCoordenadorQueDeveSerProfessorAtivo() throws Exception {
        Logado servidor = novo(Perfil.SERVIDOR);
        Logado professor = novo(Perfil.PROFESSOR);
        Logado aluno = novo(Perfil.ALUNO);
        String cursoId = criarCurso(servidor, codigo());
        String corpo = "{\"professorId\":\"%s\"}";

        mvc.perform(json(post("/cursos/{id}/coordenadores", cursoId).header("Authorization", servidor.bearer()),
                        corpo.formatted(aluno.id())))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(json(post("/cursos/{id}/coordenadores", cursoId).header("Authorization", servidor.bearer()),
                        corpo.formatted(UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(json(post("/cursos/{id}/coordenadores", cursoId).header("Authorization", servidor.bearer()),
                        corpo.formatted(professor.id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.professorId").value(professor.id().toString()));
        mvc.perform(json(post("/cursos/{id}/coordenadores", cursoId).header("Authorization", servidor.bearer()),
                        corpo.formatted(professor.id())))
                .andExpect(status().isConflict());

        mvc.perform(json(delete("/cursos/{id}/coordenadores", cursoId).header("Authorization", servidor.bearer()),
                        corpo.formatted(professor.id())))
                .andExpect(status().isNoContent());
        mvc.perform(json(delete("/cursos/{id}/coordenadores", cursoId).header("Authorization", servidor.bearer()),
                        corpo.formatted(professor.id())))
                .andExpect(status().isNotFound());
    }

    @Test
    void professorInativoNaoPodeSerCoordenador() throws Exception {
        Logado servidor = novo(Perfil.SERVIDOR);
        Logado professor = novo(Perfil.PROFESSOR);
        Usuario u = usuarios.findById(professor.id()).orElseThrow();
        u.sincronizar(u.getNome(), u.getCpf(), u.getPerfil(), false);
        usuarios.saveAndFlush(u);
        String cursoId = criarCurso(servidor, codigo());

        mvc.perform(json(post("/cursos/{id}/coordenadores", cursoId).header("Authorization", servidor.bearer()),
                        "{\"professorId\":\"%s\"}".formatted(professor.id())))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void servidorListaCoordenadoresDoCursoEOutrosPerfisRecebem403() throws Exception {
        Logado servidor = novo(Perfil.SERVIDOR);
        Logado professor = novo(Perfil.PROFESSOR);
        String cursoId = criarCurso(servidor, codigo());
        mvc.perform(json(post("/cursos/{id}/coordenadores", cursoId).header("Authorization", servidor.bearer()),
                "{\"professorId\":\"%s\"}".formatted(professor.id()))).andExpect(status().isCreated());

        mvc.perform(get("/cursos/{id}/coordenadores", cursoId).header("Authorization", servidor.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].professorId").value(professor.id().toString()));
        mvc.perform(get("/cursos/{id}/coordenadores", cursoId).header("Authorization", professor.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/cursos/{id}/coordenadores", UUID.randomUUID()).header("Authorization", servidor.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void professorSoListaSolicitacoesDosCursosQueCoordenaEServidorFiltraPorCurso() throws Exception {
        Logado servidor = novo(Perfil.SERVIDOR);
        Logado professor = novo(Perfil.PROFESSOR);
        Logado aluno = novo(Perfil.ALUNO);
        String meuCurso = criarCurso(servidor, codigo());
        String outroCurso = criarCurso(servidor, codigo());
        String minhaDisciplina = criarDisciplina(servidor, meuCurso, "BD1");
        String outraDisciplina = criarDisciplina(servidor, outroCurso, "BD1");
        mvc.perform(json(post("/cursos/{id}/coordenadores", meuCurso).header("Authorization", servidor.bearer()),
                "{\"professorId\":\"%s\"}".formatted(professor.id()))).andExpect(status().isCreated());
        String corpo = """
                {"alunoId":"%s","disciplinaDestinoId":"%s","disciplinas":[
                  {"nome":"BD I","instituicao":"UFLA","cargaHoraria":64,"ementa":"%s"}]}""";
        MvcResult mia = mvc.perform(json(post("/aproveitamentos").header("Authorization", aluno.bearer()),
                corpo.formatted(aluno.id(), minhaDisciplina, EMENTA))).andExpect(status().isCreated()).andReturn();
        mvc.perform(json(post("/aproveitamentos").header("Authorization", aluno.bearer()),
                corpo.formatted(aluno.id(), outraDisciplina, EMENTA))).andExpect(status().isCreated());
        String idMinha = JsonPath.read(mia.getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/aproveitamentos").header("Authorization", professor.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(idMinha));
        mvc.perform(get("/aproveitamentos").param("cursoId", outroCurso)
                        .header("Authorization", servidor.bearer()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(org.hamcrest.Matchers.not(idMinha)));
        mvc.perform(get("/aproveitamentos/{id}", idMinha).header("Authorization", professor.bearer()))
                .andExpect(jsonPath("$.cursoId").value(meuCurso))
                .andExpect(jsonPath("$.ementaDestino").value(EMENTA));
    }

    // ---------- solicitacao a partir do catalogo ----------

    @Test
    void alunoCriaSolicitacaoComDisciplinaDestinoIdECopiaOsTextos() throws Exception {
        Logado servidor = novo(Perfil.SERVIDOR);
        Logado aluno = novo(Perfil.ALUNO);
        String cursoId = criarCurso(servidor, codigo());
        String disciplinaId = criarDisciplina(servidor, cursoId, "BD1");

        MvcResult r = mvc.perform(json(post("/aproveitamentos").header("Authorization", aluno.bearer()), """
                        {"alunoId":"%s","disciplinaDestinoId":"%s","disciplinas":[
                          {"nome":"BD I","instituicao":"UFLA","cargaHoraria":64,"ementa":"%s"}]}
                        """.formatted(aluno.id(), disciplinaId, EMENTA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.disciplinaDestino").value("Banco de Dados"))
                .andReturn();

        UUID id = UUID.fromString(JsonPath.read(r.getResponse().getContentAsString(), "$.id"));
        Solicitacao s = solicitacoes.findById(id).orElseThrow();
        assertThat(s.getDisciplinaCursoDestino().getId().toString()).isEqualTo(disciplinaId);
        assertThat(s.getCargaHorariaDestino()).isEqualTo(60);
        assertThat(s.getCurso()).startsWith("Curso C");
        assertThat(cursadas.findBySolicitacaoId(id)).singleElement()
                .satisfies(d -> assertThat(d.getEmenta()).isEqualTo(EMENTA));

        mvc.perform(get("/aproveitamentos/{id}", id).header("Authorization", aluno.bearer()))
                .andExpect(jsonPath("$.disciplinas[0].ementa").value(EMENTA));
    }

    @Test
    void criarSolicitacaoExigeEmentaEDestinoValido() throws Exception {
        Logado servidor = novo(Perfil.SERVIDOR);
        Logado aluno = novo(Perfil.ALUNO);
        String cursoId = criarCurso(servidor, codigo());
        String disciplinaId = criarDisciplina(servidor, cursoId, "BD1");

        mvc.perform(json(post("/aproveitamentos").header("Authorization", aluno.bearer()), """
                        {"alunoId":"%s","disciplinaDestinoId":"%s","disciplinas":[
                          {"nome":"BD I","instituicao":"UFLA","cargaHoraria":64}]}
                        """.formatted(aluno.id(), disciplinaId)))
                .andExpect(status().isBadRequest());

        mvc.perform(json(post("/aproveitamentos").header("Authorization", aluno.bearer()), """
                        {"alunoId":"%s","disciplinaDestinoId":"%s","disciplinas":[
                          {"nome":"BD I","instituicao":"UFLA","cargaHoraria":64,"ementa":"x"}]}
                        """.formatted(aluno.id(), UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(post("/cursos/{id}/disciplinas/{d}/inativar", cursoId, disciplinaId)
                .header("Authorization", servidor.bearer())).andExpect(status().isOk());
        mvc.perform(json(post("/aproveitamentos").header("Authorization", aluno.bearer()), """
                        {"alunoId":"%s","disciplinaDestinoId":"%s","disciplinas":[
                          {"nome":"BD I","instituicao":"UFLA","cargaHoraria":64,"ementa":"x"}]}
                        """.formatted(aluno.id(), disciplinaId)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void coordenadorDoCursoPodeAnalisarEOutroProfessorNao() throws Exception {
        Logado servidor = novo(Perfil.SERVIDOR);
        Logado aluno = novo(Perfil.ALUNO);
        Logado coordenador = novo(Perfil.PROFESSOR);
        Logado outro = novo(Perfil.PROFESSOR);
        String cursoId = criarCurso(servidor, codigo());
        String disciplinaId = criarDisciplina(servidor, cursoId, "BD1");
        mvc.perform(json(post("/cursos/{id}/coordenadores", cursoId).header("Authorization", servidor.bearer()),
                "{\"professorId\":\"%s\"}".formatted(coordenador.id()))).andExpect(status().isCreated());

        MvcResult r = mvc.perform(json(post("/aproveitamentos").header("Authorization", aluno.bearer()), """
                        {"alunoId":"%s","disciplinaDestinoId":"%s","disciplinas":[
                          {"nome":"BD I","instituicao":"UFLA","cargaHoraria":64,"ementa":"%s"}]}
                        """.formatted(aluno.id(), disciplinaId, EMENTA)))
                .andExpect(status().isCreated()).andReturn();
        String id = JsonPath.read(r.getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/aproveitamentos/{id}/enviar", id).header("Authorization", aluno.bearer()))
                .andExpect(status().isOk());
        mvc.perform(post("/aproveitamentos/{id}/conferir", id).header("Authorization", servidor.bearer()))
                .andExpect(status().isOk());
        mvc.perform(post("/aproveitamentos/{id}/concluir-pre-diagnostico", id)
                .header("Authorization", servidor.bearer())).andExpect(status().isOk());

        mvc.perform(post("/aproveitamentos/{id}/analisar", id).header("Authorization", coordenador.bearer()))
                .andExpect(status().isOk());
        mvc.perform(post("/aproveitamentos/{id}/deferir", id).header("Authorization", outro.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/aproveitamentos/{id}/deferir", id).header("Authorization", coordenador.bearer()))
                .andExpect(status().isOk());
        mvc.perform(post("/aproveitamentos/{id}/parecer-ia/regerar", id).header("Authorization", outro.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/aproveitamentos/{id}/parecer-ia/regerar", id)
                .header("Authorization", coordenador.bearer())).andExpect(status().isAccepted());
    }
}
