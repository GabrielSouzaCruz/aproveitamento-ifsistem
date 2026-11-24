package ifsuldeminas.edu.br.ifsystem.aproveitamento.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Configuracao publica para o front: URL do portal externo (PORTAL_URL) e dados do login unico (ifsistem-auth). */
@RestController
public class ConfigController {
    private final String portalUrl;
    private final String authUrl;

    public ConfigController(@Value("${portal.url:#}") String portalUrl,
                            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String authUrl) {
        this.portalUrl = portalUrl;
        this.authUrl = authUrl.replaceAll("/+$", "");
    }

    @GetMapping("/config")
    public Map<String, String> config() {
        return Map.of("portalUrl", portalUrl, "authUrl", authUrl, "clientId", "aproveitamento");
    }
}
