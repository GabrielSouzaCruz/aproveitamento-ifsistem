package ifsuldeminas.edu.br.ifsystem.auth;

import com.jayway.jsonpath.JsonPath;
import ifsuldeminas.edu.br.ifsystem.auth.service.JwtService;
import ifsuldeminas.edu.br.ifsystem.usuario.api.UsuarioDtos.CriarUsuario;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.StatusUsuario;
import ifsuldeminas.edu.br.ifsystem.usuario.repository.UsuarioRepository;
import ifsuldeminas.edu.br.ifsystem.usuario.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static ifsuldeminas.edu.br.ifsystem.auth.AuthTestSupport.cpfAleatorio;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fluxo real de autenticacao: banco H2, filtro JWT, SecurityConfig e controllers. */
@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {
    private static final String SENHA = "senha-segura-1";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UsuarioService usuarios;
    @Autowired
    private UsuarioRepository repositorio;
    @Autowired
    private JwtService jwt;

    private static String corpoCadastro(String cpf, String email, String senha) {
        return """
                {"cpf":"%s","nome":"Aluno Teste","email":"%s","matricula":"M-%s","senha":"%s"}
                """.formatted(cpf, email, UUID.randomUUID(), senha);
    }

    private static String email() {
        return "t" + UUID.randomUUID().toString().substring(0, 8) + "@ifsuldeminas.edu.br";
    }

    private static String mascarar(String cpf) {
        return cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9);
    }

    private static String corpoLogin(String cpf, String senha) {
        return "{\"cpf\":\"%s\",\"senha\":\"%s\"}".formatted(cpf, senha);
    }

    private MockHttpServletRequestBuilder postJson(String url, String corpo) {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    private String cadastrarAluno() throws Exception {
        String cpf = cpfAleatorio();
        mvc.perform(postJson("/auth/cadastro", corpoCadastro(cpf, email(), SENHA))).andExpect(status().isCreated());
        return cpf;
    }

    private String login(String cpf, String senha) throws Exception {
        MvcResult r = mvc.perform(postJson("/auth/login", corpoLogin(cpf, senha)))
                .andExpect(status().isOk()).andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(), "$.token");
    }

    private String bearerDeServidor() throws Exception {
        String cpf = cpfAleatorio();
        usuarios.criar(new CriarUsuario("Servidor Teste", cpf, email(), "S-" + UUID.randomUUID(), SENHA,
                null, null, null), Perfil.SERVIDOR);
        return "Bearer " + login(cpf, SENHA);
    }

    private void inativar(String cpf) {
        var u = repositorio.findByCpf(cpf).orElseThrow();
        u.alterarStatus(StatusUsuario.INATIVO);
        repositorio.save(u);
    }

    // ---------- cadastro ----------

    @Test
    void cadastroCriaAlunoComSenhaEmHashECpfSoDigitos() throws Exception {
        String cpf = cpfAleatorio();
        mvc.perform(postJson("/auth/cadastro", corpoCadastro(mascarar(cpf), email(), SENHA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.perfil").value("ALUNO"))
                .andExpect(jsonPath("$.cpf").value(cpf))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.senhaHash").doesNotExist());
        var salvo = repositorio.findByCpf(cpf).orElseThrow();
        assertThat(salvo.getSenhaHash()).startsWith("$2").isNotEqualTo(SENHA);
        assertThat(salvo.isAtivo()).isTrue();
    }

    @Test
    void cadastroIgnoraPerfilEnviadoNoCorpo() throws Exception {
        String corpo = corpoCadastro(cpfAleatorio(), email(), SENHA).replace("{", "{\"perfil\":\"SERVIDOR\",");
        mvc.perform(postJson("/auth/cadastro", corpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.perfil").value("ALUNO"));
    }

    @Test
    void cadastroComCpfDuplicadoRetorna409() throws Exception {
        String cpf = cadastrarAluno();
        mvc.perform(postJson("/auth/cadastro", corpoCadastro(cpf, email(), SENHA))).andExpect(status().isConflict());
    }

    @Test
    void cadastroComEmailDuplicadoRetorna409() throws Exception {
        String email = email();
        mvc.perform(postJson("/auth/cadastro", corpoCadastro(cpfAleatorio(), email, SENHA)))
                .andExpect(status().isCreated());
        mvc.perform(postJson("/auth/cadastro", corpoCadastro(cpfAleatorio(), email.toUpperCase(), SENHA)))
                .andExpect(status().isConflict());
    }

    @Test
    void cadastroAceitaQualquerDominioDeEmailMasExigeFormatoValido() throws Exception {
        mvc.perform(postJson("/auth/cadastro",
                        corpoCadastro(cpfAleatorio(), "t" + UUID.randomUUID().toString().substring(0, 8) + "@gmail.com", SENHA)))
                .andExpect(status().isCreated());
        mvc.perform(postJson("/auth/cadastro", corpoCadastro(cpfAleatorio(), "semarroba.com", SENHA)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cadastroComSenhaCurtaOuCpfInvalidoRetorna400() throws Exception {
        mvc.perform(postJson("/auth/cadastro", corpoCadastro(cpfAleatorio(), email(), "1234567")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson("/auth/cadastro", corpoCadastro("11111111111", email(), SENHA)))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson("/auth/cadastro", corpoCadastro("52998224726", email(), SENHA)))
                .andExpect(status().isBadRequest());
    }

    // ---------- login ----------

    @Test
    void loginCorretoRetornaTokenExpiracaoEUsuario() throws Exception {
        String cpf = cadastrarAluno();
        MvcResult r = mvc.perform(postJson("/auth/login", corpoLogin(cpf, SENHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiraEm").isNotEmpty())
                .andExpect(jsonPath("$.usuario.id").isNotEmpty())
                .andExpect(jsonPath("$.usuario.nome").value("Aluno Teste"))
                .andExpect(jsonPath("$.usuario.perfil").value("ALUNO"))
                .andReturn();
        String corpo = r.getResponse().getContentAsString();
        assertThat(jwt.usuarioId(JsonPath.<String>read(corpo, "$.token")).toString())
                .isEqualTo(JsonPath.<String>read(corpo, "$.usuario.id"));
    }

    @Test
    void loginAceitaCpfComMascara() throws Exception {
        login(mascarar(cadastrarAluno()), SENHA);
    }

    @Test
    void loginComSenhaErradaOuUsuarioInexistenteRetorna401Generico() throws Exception {
        String cpf = cadastrarAluno();
        String senhaErrada = mvc.perform(postJson("/auth/login", corpoLogin(cpf, "errada-123")))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String inexistente = mvc.perform(postJson("/auth/login", corpoLogin(cpfAleatorio(), SENHA)))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        // Mesma mensagem nos dois casos: nao revela se o CPF existe.
        assertThat(senhaErrada).isEqualTo(inexistente);
    }

    @Test
    void loginDeUsuarioInativoRetorna401() throws Exception {
        String cpf = cadastrarAluno();
        inativar(cpf);
        mvc.perform(postJson("/auth/login", corpoLogin(cpf, SENHA))).andExpect(status().isUnauthorized());
    }

    // ---------- protecao dos endpoints ----------

    @Test
    void semTokenOsEndpointsProtegidosRetornam401EmJson() throws Exception {
        mvc.perform(get("/aproveitamentos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/usuarios")).andExpect(status().isUnauthorized());
    }

    @Test
    void headersAntigosNaoAutenticam() throws Exception {
        mvc.perform(get("/aproveitamentos")
                        .header("X-Usuario-Id", UUID.randomUUID().toString()).header("X-Perfil", "SERVIDOR"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenInvalidoOuAdulteradoRetorna401() throws Exception {
        mvc.perform(get("/aproveitamentos").header("Authorization", "Bearer lixo.lixo.lixo"))
                .andExpect(status().isUnauthorized());
        String token = login(cadastrarAluno(), SENHA);
        String adulterado = token.substring(0, token.length() - 3) + "xyz";
        mvc.perform(get("/aproveitamentos").header("Authorization", "Bearer " + adulterado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenValidoAutenticaEUsuarioInativadoPerdeAcesso() throws Exception {
        String cpf = cadastrarAluno();
        String bearer = "Bearer " + login(cpf, SENHA);
        mvc.perform(get("/aproveitamentos").header("Authorization", bearer)).andExpect(status().isOk());

        inativar(cpf);
        mvc.perform(get("/aproveitamentos").header("Authorization", bearer)).andExpect(status().isUnauthorized());
    }

    // ---------- /usuarios (secretaria) ----------

    @Test
    void alunoRecebe403EmUsuarios() throws Exception {
        String bearer = "Bearer " + login(cadastrarAluno(), SENHA);
        mvc.perform(get("/usuarios").header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(postJson("/usuarios", "{}").header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(patch("/usuarios/{id}/ativo", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ativo\":false}").header("Authorization", bearer)).andExpect(status().isForbidden());
    }

    @Test
    void servidorCriaProfessorListaEAlteraAtivo() throws Exception {
        String bearer = bearerDeServidor();
        String cpf = cpfAleatorio();
        MvcResult criado = mvc.perform(postJson("/usuarios", """
                        {"cpf":"%s","nome":"Prof Teste","email":"%s","senha":"%s","perfil":"PROFESSOR"}
                        """.formatted(cpf, email(), SENHA)).header("Authorization", bearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.perfil").value("PROFESSOR"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andReturn();
        String id = JsonPath.read(criado.getResponse().getContentAsString(), "$.id");

        MvcResult lista = mvc.perform(get("/usuarios").param("perfil", "PROFESSOR").header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn();
        String corpoLista = lista.getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(corpoLista, "$[*].perfil")).containsOnly("PROFESSOR");
        assertThat(JsonPath.<List<String>>read(corpoLista, "$[*].id")).contains(id);

        mvc.perform(patch("/usuarios/{id}/ativo", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativo\":false}").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(false));
        mvc.perform(postJson("/auth/login", corpoLogin(cpf, SENHA))).andExpect(status().isUnauthorized());
    }

    @Test
    void servidorNaoCriaAlunoPorUsuariosEAtivoDeIdInexistenteDa404() throws Exception {
        String bearer = bearerDeServidor();
        mvc.perform(postJson("/usuarios", """
                        {"cpf":"%s","nome":"X","email":"%s","senha":"%s","perfil":"ALUNO"}
                        """.formatted(cpfAleatorio(), email(), SENHA)).header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/usuarios/{id}/ativo", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativo\":false}").header("Authorization", bearer))
                .andExpect(status().isNotFound());
    }
}
