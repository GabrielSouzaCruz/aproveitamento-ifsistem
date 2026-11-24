package ifsuldeminas.edu.br.ifsystem.aproveitamento.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Configuracao publica para o front: URL do portal externo (PORTAL_URL). */
@RestController
public class ConfigController {
    private final String portalUrl;

    public ConfigController(@Value("${portal.url:#}") String portalUrl) {
        this.portalUrl = portalUrl;
    }

    @GetMapping("/config")
    public Map<String, String> config() {
        return Map.of("portalUrl", portalUrl);
    }
}
