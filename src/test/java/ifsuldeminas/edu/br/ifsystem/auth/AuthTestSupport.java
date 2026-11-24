package ifsuldeminas.edu.br.ifsystem.auth;

import ifsuldeminas.edu.br.ifsystem.usuario.domain.Perfil;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Helpers de teste: tokens do ifsistem-auth (claims sub, perfil, nome, cpf) sem assinatura real. */
public final class AuthTestSupport {
    public static final String CPF_PADRAO = "52998224725";

    private AuthTestSupport() {
    }

    /** Autentica a requisicao como o usuario, com as claims e o papel (ROLE_<PERFIL>) que o auth emitiria. */
    public static RequestPostProcessor jwt(UUID id, Perfil perfil) {
        return jwt(id, perfil, CPF_PADRAO);
    }

    public static RequestPostProcessor jwt(UUID id, Perfil perfil, String cpf) {
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(j -> j.subject(id.toString()).claim("perfil", perfil.name())
                        .claim("nome", "Fulano " + perfil).claim("cpf", cpf))
                .authorities(new SimpleGrantedAuthority("ROLE_" + perfil.name()));
    }

    /** Token "falso" aceito pelo {@link TestJwtDecoderConfig}, para os testes @SpringBootTest com header Authorization. */
    public static String bearer(UUID id, Perfil perfil, String cpf) {
        return "Bearer teste." + id + "." + perfil + "." + cpf;
    }

    /** O Jwt que o {@link TestJwtDecoderConfig} devolve para um token de {@link #bearer}. */
    static Jwt decodificar(String token) {
        String[] p = token.split("\\.");
        if (p.length != 4 || !p[0].equals("teste"))
            throw new org.springframework.security.oauth2.jwt.BadJwtException("token de teste invalido");
        return new Jwt(token, Instant.now(), Instant.now().plusSeconds(3600), Map.of("alg", "none"),
                Map.of("sub", p[1], "perfil", p[2], "cpf", p[3], "nome", "Teste " + p[2]));
    }

    /** CPF valido aleatorio (so digitos), para testes que nao podem colidir. */
    public static String cpfAleatorio() {
        var r = java.util.concurrent.ThreadLocalRandom.current();
        int[] d = new int[11];
        do {
            for (int i = 0; i < 9; i++) d[i] = r.nextInt(10);
        } while (java.util.Arrays.stream(d, 0, 9).distinct().count() == 1);
        for (int n = 9; n < 11; n++) {
            int soma = 0;
            for (int i = 0; i < n; i++) soma += d[i] * (n + 1 - i);
            d[n] = (soma * 10 % 11) % 10;
        }
        StringBuilder sb = new StringBuilder();
        for (int x : d) sb.append(x);
        return sb.toString();
    }
}
