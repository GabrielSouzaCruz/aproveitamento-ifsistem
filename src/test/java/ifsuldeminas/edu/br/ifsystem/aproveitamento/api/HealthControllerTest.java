package ifsuldeminas.edu.br.ifsystem.aproveitamento.api;

import ifsuldeminas.edu.br.ifsystem.auth.config.SecurityConfig;
import ifsuldeminas.edu.br.ifsystem.auth.service.JwtService;
import ifsuldeminas.edu.br.ifsystem.usuario.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** So HealthController + seguranca: sem repositorio nem DataSource, logo sem tocar no banco. */
@WebMvcTest(HealthController.class)
@Import({SecurityConfig.class, JwtService.class})
class HealthControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private UsuarioService usuarioService;

    @Test
    void healthResponde200SemToken() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"ok\"}"));
    }
}
