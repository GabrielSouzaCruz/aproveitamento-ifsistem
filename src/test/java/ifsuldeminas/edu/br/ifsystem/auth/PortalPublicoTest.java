package ifsuldeminas.edu.br.ifsystem.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PortalPublicoTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void raizELoginSaoPublicos() throws Exception {
        for (String caminho : new String[]{"/", "/login.html", "/callback.html", "/aproveitamento.html"}) {
            mvc.perform(get(caminho)).andExpect(status().isOk());
        }
    }

    @Test
    void configEPublicoEDevolvePortalUrl() throws Exception {
        mvc.perform(get("/config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.portalUrl").value("#"))
                .andExpect(jsonPath("$.authUrl").value("http://auth.teste.invalid"))
                .andExpect(jsonPath("$.clientId").value("aproveitamento"));
    }

    /** O menu da secretaria aponta para a gestao de usuarios do auth, cuja URL vem de /config (via sso.js). */
    @Test
    void menuDaSecretariaLigaNaGestaoDeUsuariosDoAuth() throws Exception {
        mvc.perform(get("/app.js")).andExpect(status().isOk())
                .andExpect(content().string(containsString("/usuarios.html")))
                .andExpect(content().string(containsString("usuarios-externo")));
        mvc.perform(get("/js/sso.js")).andExpect(status().isOk())
                .andExpect(content().string(containsString("window.IFSISTEM_AUTH_URL = authUrl")));
    }


    /** Vindo do menu do auth (aproveitamento.html#/cursos), a rota sobrevive ao login: guardada antes e restaurada no callback. */
    @Test
    void rotaDoHashSobreviveAoCicloDeLogin() throws Exception {
        mvc.perform(get("/js/sso.js")).andExpect(status().isOk())
                .andExpect(content().string(containsString("function entrar()")))
                .andExpect(content().string(containsString("function rotaPretendida()")));
        mvc.perform(get("/callback.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("'aproveitamento.html' + rotaPretendida()")));
        mvc.perform(get("/js/inicio.js")).andExpect(status().isOk())
                .andExpect(content().string(containsString("return entrar()")));
    }
    @Test
    void portalInternoFoiRemovido() throws Exception {
        mvc.perform(get("/portal.js")).andExpect(status().isUnauthorized());
        mvc.perform(get("/modulos.json")).andExpect(status().isUnauthorized());
    }

    @Test
    void endpointsProtegidosContinuamExigindoToken() throws Exception {
        mvc.perform(get("/professores")).andExpect(status().isUnauthorized());
        mvc.perform(get("/aproveitamentos")).andExpect(status().isUnauthorized());
    }
}
