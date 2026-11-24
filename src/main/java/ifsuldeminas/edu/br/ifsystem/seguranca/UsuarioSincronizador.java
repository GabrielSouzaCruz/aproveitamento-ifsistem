package ifsuldeminas.edu.br.ifsystem.seguranca;

import ifsuldeminas.edu.br.ifsystem.usuario.service.UsuarioLocalService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Copia o usuario do token para public.usuarios (upsert: id = sub, nome, cpf, perfil, ativo), para as telas
 * enxergarem nomes e a vinculacao de coordenador checar PROFESSOR ativo. A fonte da verdade e o auth.
 * So grava na primeira requisicao de um sub (ou quando as claims mudam); depois fica em memoria.
 * O SecurityConfig o encaixa na cadeia de seguranca e desliga o registro automatico como filtro de servlet.
 */
@Component
public class UsuarioSincronizador extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(UsuarioSincronizador.class);

    private final UsuarioLocalService locais;
    private final Map<UUID, UsuarioAutenticado> sincronizados = new ConcurrentHashMap<>();

    public UsuarioSincronizador(UsuarioLocalService locais) {
        this.locais = locais;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        UsuarioAutenticado u = UsuarioAutenticado.de(SecurityContextHolder.getContext().getAuthentication());
        if (u != null)
            sincronizar(u);
        chain.doFilter(request, response);
    }

    public void sincronizar(UsuarioAutenticado u) {
        if (u.equals(sincronizados.get(u.id())))
            return;
        try {
            locais.gravar(u.id(), u.nome(), u.cpf(), u.perfil(), true);
            sincronizados.put(u.id(), u);
        } catch (RuntimeException e) {
            // Ex.: CPF ja usado por outro id local. A requisicao segue; so a copia local fica defasada.
            log.warn("Nao foi possivel sincronizar o usuario {}: {}", u.id(), e.getMessage());
        }
    }
}
