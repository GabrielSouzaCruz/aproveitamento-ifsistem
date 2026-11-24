package ifsuldeminas.edu.br.ifsystem.seguranca;

import ifsuldeminas.edu.br.ifsystem.usuario.domain.Perfil;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

/** Usuario da requisicao, lido das claims do JWT emitido pelo ifsistem-auth (sub, perfil, nome, cpf). */
public record UsuarioAutenticado(UUID id, Perfil perfil, String nome, String cpf) {

    /** Null se a autenticacao nao e um JWT com sub (UUID) e perfil conhecidos. */
    public static UsuarioAutenticado de(Authentication auth) {
        if (!(auth instanceof JwtAuthenticationToken token))
            return null;
        return de(token.getToken());
    }

    public static UsuarioAutenticado de(Jwt jwt) {
        try {
            return new UsuarioAutenticado(UUID.fromString(jwt.getSubject()),
                    Perfil.valueOf(jwt.getClaimAsString("perfil")),
                    jwt.getClaimAsString("nome"), jwt.getClaimAsString("cpf"));
        } catch (RuntimeException e) { // sub ou perfil ausentes/invalidos
            return null;
        }
    }
}
