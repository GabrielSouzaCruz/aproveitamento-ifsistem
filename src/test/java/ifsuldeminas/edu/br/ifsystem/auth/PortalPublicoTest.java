package ifsuldeminas.edu.br.ifsystem.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
