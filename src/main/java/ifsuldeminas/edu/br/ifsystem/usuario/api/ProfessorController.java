package ifsuldeminas.edu.br.ifsystem.usuario.api;

import ifsuldeminas.edu.br.ifsystem.seguranca.UsuarioAutenticado;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Cpf;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.StatusUsuario;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Usuario;
import ifsuldeminas.edu.br.ifsystem.usuario.repository.UsuarioRepository;
import ifsuldeminas.edu.br.ifsystem.usuario.service.AuthClient;
import ifsuldeminas.edu.br.ifsystem.usuario.service.UsuarioLocalService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** Professores para a tela de coordenadores (somente SERVIDOR). A gestao dos usuarios e no ifsistem-auth. */
@RestController
@RequestMapping("/professores")
public class ProfessorController {
    public record ProfessorResposta(UUID id, String nome, String cpf) {
        static ProfessorResposta de(Usuario u) {
            return new ProfessorResposta(u.getId(), u.getNome(), u.getCpf());
        }
    }

    private final UsuarioRepository usuarios;
    private final UsuarioLocalService locais;
    private final AuthClient auth;

    public ProfessorController(UsuarioRepository usuarios, UsuarioLocalService locais, AuthClient auth) {
        this.usuarios = usuarios;
        this.locais = locais;
        this.auth = auth;
    }

    /**
     * Sem parametro: professores ativos ja sincronizados (entraram ao menos uma vez). Com {@code cpf}: procura
     * tambem no auth (GET /usuarios?perfil=PROFESSOR com o token do servidor) e sincroniza o encontrado.
     */
    @GetMapping
    public List<ProfessorResposta> listar(@RequestParam(required = false) String cpf,
                                          @AuthenticationPrincipal Jwt jwt) {
        UsuarioAutenticado logado = UsuarioAutenticado.de(jwt);
        if (logado == null || logado.perfil() != Perfil.SERVIDOR)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas a secretaria");
        List<Usuario> ativos;
        if (cpf != null && !cpf.isBlank()) {
            String digitos = Cpf.normalizar(cpf);
            auth.listarProfessores(jwt.getTokenValue()).stream()
                    .filter(p -> p.perfil() == Perfil.PROFESSOR && digitos.equals(Cpf.normalizar(p.cpf())))
                    .findFirst()
                    .ifPresent(p -> locais.gravar(p.id(), p.nome(), p.cpf(), Perfil.PROFESSOR, p.ativo()));
            ativos = usuarios.findByPerfilAndStatusOrderByNome(Perfil.PROFESSOR, StatusUsuario.ATIVO).stream()
                    .filter(u -> digitos.equals(u.getCpf())).toList();
        } else {
            ativos = usuarios.findByPerfilAndStatusOrderByNome(Perfil.PROFESSOR, StatusUsuario.ATIVO);
        }
        return ativos.stream().map(ProfessorResposta::de).toList();
    }
}
