package ifsuldeminas.edu.br.ifsystem.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PortalPublicoTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void portalEModulosSaoPublicos() throws Exception {
        for (String caminho : new String[]{"/", "/index.html", "/portal.js", "/modulos.json",
                "/login.html", "/aproveitamento.html"}) {
            mvc.perform(get(caminho)).andExpect(status().isOk());
        }
    }

    @Test
    void endpointsProtegidosContinuamExigindoToken() throws Exception {
        mvc.perform(get("/usuarios")).andExpect(status().isUnauthorized());
        mvc.perform(get("/aproveitamentos")).andExpect(status().isUnauthorized());
    }
}
