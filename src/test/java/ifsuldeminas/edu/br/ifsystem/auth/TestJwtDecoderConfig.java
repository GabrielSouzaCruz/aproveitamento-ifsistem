package ifsuldeminas.edu.br.ifsystem.auth;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** JwtDecoder de teste: entende os tokens de {@link AuthTestSupport#bearer}; o resto e rejeitado (401). */
@TestConfiguration
public class TestJwtDecoderConfig {
    @Bean
    @Primary
    JwtDecoder jwtDecoderDeTeste() {
        return AuthTestSupport::decodificar;
    }
}
