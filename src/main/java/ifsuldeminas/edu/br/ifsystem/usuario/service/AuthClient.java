package ifsuldeminas.edu.br.ifsystem.usuario.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Perfil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** Consulta a API /usuarios do ifsistem-auth com o token do proprio usuario (o auth so aceita SERVIDOR). */
@Component
public class AuthClient {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UsuarioAuth(UUID id, String nome, String cpf, Perfil perfil, boolean ativo) {
    }

    private final RestClient http;

    public AuthClient(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String authUrl) {
        this.http = RestClient.builder().baseUrl(authUrl.replaceAll("/+$", "")).build();
    }

    public List<UsuarioAuth> listarProfessores(String accessToken) {
        try {
            List<UsuarioAuth> r = http.get().uri("/usuarios?perfil=PROFESSOR")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve().body(new ParameterizedTypeReference<List<UsuarioAuth>>() {
                    });
            return r == null ? List.of() : r;
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "O ifsistem-auth nao aceitou a consulta de professores: " + e.getMessage());
        }
    }
}
