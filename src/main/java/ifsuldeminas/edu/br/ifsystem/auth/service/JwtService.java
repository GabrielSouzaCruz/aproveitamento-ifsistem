package ifsuldeminas.edu.br.ifsystem.auth.service;

import ifsuldeminas.edu.br.ifsystem.usuario.domain.Usuario;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/** JWT HS256. Claims: sub=id do usuario, perfil, nome. */
@Service
public class JwtService {
    private final SecretKey key;
    private final long expiracaoMs;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-ms}") long expiracaoMs) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32)
            throw new IllegalArgumentException("app.jwt.secret (JWT_SECRET) deve possuir pelo menos 32 bytes");
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expiracaoMs = expiracaoMs;
    }

    public record TokenEmitido(String token, Instant expiraEm) {
    }

    public TokenEmitido gerar(Usuario u) {
        Date agora = new Date();
        Date expira = new Date(agora.getTime() + expiracaoMs);
        String token = Jwts.builder().subject(u.getId().toString())
                .claim("perfil", u.getPerfil().name())
                .claim("nome", u.getNome())
                .issuedAt(agora).expiration(expira)
                .signWith(key, Jwts.SIG.HS256).compact();
        return new TokenEmitido(token, expira.toInstant());
    }

    /** Id do usuario do token; lanca JwtException/IllegalArgumentException se invalido ou expirado. */
    public UUID usuarioId(String token) {
        String sub = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
        return UUID.fromString(sub);
    }
}
