package ifsuldeminas.edu.br.ifsystem.auth;

import ifsuldeminas.edu.br.ifsystem.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.StatusUsuario;
import ifsuldeminas.edu.br.ifsystem.usuario.repository.UsuarioRepository;
import ifsuldeminas.edu.br.ifsystem.usuario.service.AuthClient;
import ifsuldeminas.edu.br.ifsystem.usuario.service.AuthClient.UsuarioAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ifsuldeminas.edu.br.ifsystem.auth.AuthTestSupport.bearer;
import static ifsuldeminas.edu.br.ifsystem.auth.AuthTestSupport.cpfAleatorio;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Resource server: perfil vira papel, 401 em JSON, 403 por perfil e copia local do usuario (upsert pelas claims). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtDecoderConfig.class)
class SegurancaSsoTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtAuthenticationConverter conversor;
    @Autowired
    private UsuarioRepository usuarios;
    @MockitoBean
    private AuthClient authClient;

    private static Jwt jwt(String perfil) {
        Map<String, Object> claims = perfil == null
                ? Map.of("sub", UUID.randomUUID().toString())
                : Map.of("sub", UUID.randomUUID().toString(), "perfil", perfil);
        return new Jwt("t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), claims);
    }

    /** So os papeis (ROLE_*): o Spring Security 7 ainda acrescenta authorities de fator (FACTOR_BEARER). */
    private List<String> papeis(Jwt jwt) {
        return conversor.convert(jwt).getAuthorities().stream().map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_")).toList();
    }

    @Test
    void perfilViraRolePrefixadaComROLE() {
        for (Perfil p : Perfil.values())
            assertThat(papeis(jwt(p.name()))).containsExactly("ROLE_" + p.name());
    }

    @Test
    void tokenSemPerfilNaoGanhaPapelNenhum() {
        assertThat(papeis(jwt(null))).isEmpty();
    }

    @Test
    void semTokenOuComTokenInvalidoRetorna401EmJson() throws Exception {
        mvc.perform(get("/aproveitamentos")).andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"status\":401}"));
        mvc.perform(get("/aproveitamentos").header("Authorization", "Bearer lixo"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"status\":401}"));
    }

    @Test
    void alunoRecebe403EmRotaDeSecretaria() throws Exception {
        String aluno = bearer(UUID.randomUUID(), Perfil.ALUNO, cpfAleatorio());
        mvc.perform(post("/cursos").header("Authorization", aluno).contentType("application/json")
                        .content("{\"codigo\":\"X\",\"nome\":\"X\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/professores").header("Authorization", aluno)).andExpect(status().isForbidden());
    }

    @Test
    void primeiraRequisicaoCriaOUsuarioLocalAPartirDasClaims() throws Exception {
        UUID id = UUID.randomUUID();
        String cpf = cpfAleatorio();
        assertThat(usuarios.findById(id)).isEmpty();

        mvc.perform(get("/cursos").header("Authorization", bearer(id, Perfil.PROFESSOR, cpf)))
                .andExpect(status().isOk());

        assertThat(usuarios.findById(id)).get().satisfies(u -> {
            assertThat(u.getNome()).isEqualTo("Teste PROFESSOR");
            assertThat(u.getCpf()).isEqualTo(cpf);
            assertThat(u.getPerfil()).isEqualTo(Perfil.PROFESSOR);
            assertThat(u.getStatus()).isEqualTo(StatusUsuario.ATIVO);
        });
    }

    @Test
    void requisicaoSeguinteAtualizaOUsuarioQuandoAsClaimsMudam() throws Exception {
        UUID id = UUID.randomUUID();
        String cpf = cpfAleatorio();
        mvc.perform(get("/cursos").header("Authorization", bearer(id, Perfil.ALUNO, cpf))).andExpect(status().isOk());

        mvc.perform(get("/cursos").header("Authorization", bearer(id, Perfil.SERVIDOR, cpf))).andExpect(status().isOk());

        assertThat(usuarios.findById(id)).get().extracting(u -> u.getPerfil()).isEqualTo(Perfil.SERVIDOR);
        assertThat(usuarios.findAll().stream().filter(u -> u.getId().equals(id))).hasSize(1);
    }

    @Test
    void servidorListaProfessoresJaSincronizados() throws Exception {
        UUID professor = UUID.randomUUID();
        mvc.perform(get("/cursos").header("Authorization", bearer(professor, Perfil.PROFESSOR, cpfAleatorio())))
                .andExpect(status().isOk());

        mvc.perform(get("/professores").header("Authorization", bearer(UUID.randomUUID(), Perfil.SERVIDOR, cpfAleatorio())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(professor)).exists());
    }

    @Test
    void buscaPorCpfConsultaOAuthComOTokenDoServidorESincronizaOProfessor() throws Exception {
        UUID professor = UUID.randomUUID();
        String cpf = cpfAleatorio();
        String servidor = bearer(UUID.randomUUID(), Perfil.SERVIDOR, cpfAleatorio());
        when(authClient.listarProfessores(anyString())).thenReturn(List.of(
                new UsuarioAuth(professor, "Paulo Professor", cpf, Perfil.PROFESSOR, true),
                new UsuarioAuth(UUID.randomUUID(), "Outro", cpfAleatorio(), Perfil.PROFESSOR, true)));

        mvc.perform(get("/professores").param("cpf", cpf).header("Authorization", servidor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(professor.toString()))
                .andExpect(jsonPath("$[0].nome").value("Paulo Professor"));

        verify(authClient).listarProfessores(servidor.substring("Bearer ".length()));
        assertThat(usuarios.findById(professor)).isPresent();
    }

    @Test
    void buscaPorCpfComAuthRecusandoRetorna502SemContornar() throws Exception {
        when(authClient.listarProfessores(anyString())).thenThrow(
                new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O ifsistem-auth nao aceitou a consulta"));

        mvc.perform(get("/professores").param("cpf", cpfAleatorio())
                        .header("Authorization", bearer(UUID.randomUUID(), Perfil.SERVIDOR, cpfAleatorio())))
                .andExpect(status().isBadGateway());
    }
}
