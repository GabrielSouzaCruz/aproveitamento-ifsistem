package ifsuldeminas.edu.br.ifsystem.seguranca;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Resource server: valida o JWT do ifsistem-auth (assinatura e emissor); o login e la. */
@Configuration
public class SecurityConfig {

    /** perfil=ALUNO vira ROLE_ALUNO. Sem perfil, o usuario nao ganha papel nenhum. */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String perfil = jwt.getClaimAsString("perfil");
            return perfil == null ? AuthorityUtils.NO_AUTHORITIES
                    : AuthorityUtils.createAuthorityList("ROLE_" + perfil);
        });
        return converter;
    }

    /** O sincronizador roda so dentro da cadeia de seguranca (depois do Bearer), nao como filtro de servlet solto. */
    @Bean
    FilterRegistrationBean<UsuarioSincronizador> sincronizadorForaDoServlet(UsuarioSincronizador f) {
        FilterRegistrationBean<UsuarioSincronizador> r = new FilterRegistrationBean<>(f);
        r.setEnabled(false);
        return r;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter perfis,
                                            UsuarioSincronizador sincronizador) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error", "/health", "/config",
                                "/", "/index.html", "/aproveitamento.html", "/login.html", "/callback.html",
                                "/app.js", "/style.css", "/js/**", "/fonts/**", "/img/**", "/favicon.ico",
                                "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs.jwt(j -> j.jwtAuthenticationConverter(perfis))
                        .authenticationEntryPoint((req, res, ex) ->
                                json(res, HttpServletResponse.SC_UNAUTHORIZED, "Nao autenticado"))
                        .accessDeniedHandler((req, res, ex) ->
                                json(res, HttpServletResponse.SC_FORBIDDEN, "Acesso negado")))
                .addFilterAfter(sincronizador, BearerTokenAuthenticationFilter.class)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                json(res, HttpServletResponse.SC_UNAUTHORIZED, "Nao autenticado"))
                        .accessDeniedHandler((req, res, ex) ->
                                json(res, HttpServletResponse.SC_FORBIDDEN, "Acesso negado")))
                .build();
    }

    private static void json(HttpServletResponse res, int status, String erro) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.getWriter().write("{\"status\":" + status + ",\"erro\":\"" + erro + "\"}");
    }
}
