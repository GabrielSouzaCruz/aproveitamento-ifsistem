package ifsuldeminas.edu.br.ifsystem.auth.config;

import ifsuldeminas.edu.br.ifsystem.auth.service.JwtService;
import ifsuldeminas.edu.br.ifsystem.auth.service.UsuarioAutenticado;
import ifsuldeminas.edu.br.ifsystem.usuario.service.UsuarioService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Le "Authorization: Bearer", valida o token e carrega o usuario (ativo) no SecurityContext.
 * Token ausente/invalido apenas deixa a requisicao anonima; quem responde 401 e o entry point.
 * Nao e @Component: o SecurityConfig o instancia, para nao ser registrado tambem como filtro de servlet.
 */
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final UsuarioService usuarios;

    public JwtAuthFilter(JwtService jwt, UsuarioService usuarios) {
        this.jwt = jwt;
        this.usuarios = usuarios;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                usuarios.buscarAtivo(jwt.usuarioId(header.substring(7).trim())).ifPresent(u ->
                        SecurityContextHolder.getContext().setAuthentication(
                                UsernamePasswordAuthenticationToken.authenticated(
                                        new UsuarioAutenticado(u.getId(), u.getPerfil(), u.getNome()), null,
                                        List.of(new SimpleGrantedAuthority("ROLE_" + u.getPerfil().name())))));
            } catch (RuntimeException ignored) {
                // JwtException, IllegalArgumentException: token invalido -> anonimo -> 401.
            }
        }
        chain.doFilter(request, response);
    }
}
