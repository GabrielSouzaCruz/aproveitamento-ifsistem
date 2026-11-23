package ifsuldeminas.edu.br.ifsystem.usuario.service;

import ifsuldeminas.edu.br.ifsystem.usuario.api.UsuarioDtos.CriarPorSecretaria;
import ifsuldeminas.edu.br.ifsystem.usuario.api.UsuarioDtos.CriarUsuario;
import ifsuldeminas.edu.br.ifsystem.usuario.api.UsuarioDtos.Resposta;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Cpf;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Usuario;
import ifsuldeminas.edu.br.ifsystem.usuario.factory.UsuarioFactory;
import ifsuldeminas.edu.br.ifsystem.usuario.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class UsuarioService {
    static final int SENHA_MINIMA = 8;
    private static final java.util.regex.Pattern FORMATO_EMAIL =
            java.util.regex.Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UsuarioRepository repository;
    private final PasswordEncoder encoder;
    private final Map<Perfil, UsuarioFactory> factories;

    public UsuarioService(UsuarioRepository repository, PasswordEncoder encoder,
                          Map<String, UsuarioFactory> factories) {
        this.repository = repository;
        this.encoder = encoder;
        this.factories = Map.of(Perfil.ALUNO, factories.get("alunoFactory"),
                Perfil.PROFESSOR, factories.get("professorFactory"),
                Perfil.SERVIDOR, factories.get("servidorFactory"),
                Perfil.ADMINISTRADOR, factories.get("administradorFactory"));
    }

    /** Aceita CPF com ou sem mascara. */
    public Optional<Usuario> buscarPorCpf(String cpf) {
        return repository.findByCpf(Cpf.normalizar(cpf));
    }

    /** Usuario existente e ativo; usado pelo filtro JWT a cada requisicao. */
    public Optional<Usuario> buscarAtivo(UUID id) {
        return repository.findById(id).filter(Usuario::isAtivo);
    }

    public Usuario criar(CriarUsuario d, Perfil perfil) {
        if (d == null || isBlank(d.nome()) || isBlank(d.cpf()) || isBlank(d.email())
                || isBlank(d.matricula()) || isBlank(d.senha()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados obrigatorios ausentes");
        if (d.senha().length() < SENHA_MINIMA)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A senha deve possuir ao menos " + SENHA_MINIMA + " caracteres");
        if (!Cpf.valido(d.cpf()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CPF invalido");
        validarEmail(d.email());
        String cpf = Cpf.normalizar(d.cpf());
        String email = d.email().trim().toLowerCase();
        if (repository.existsByCpf(cpf) || repository.existsByEmail(email)
                || repository.existsByMatricula(d.matricula().trim()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CPF, email ou matricula ja cadastrados");
        CriarUsuario normalizado = new CriarUsuario(d.nome().trim(), cpf, email, d.matricula().trim(),
                d.senha(), d.telefone(), d.curso(), d.periodo());
        return repository.save(factories.get(perfil).criar(normalizado, encoder.encode(d.senha())));
    }

    /** Criacao pela secretaria: PROFESSOR ou SERVIDOR; a matricula e gerada a partir do CPF. */
    public Usuario criarPorSecretaria(CriarPorSecretaria d) {
        if (d == null || d.perfil() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados obrigatorios ausentes");
        if (d.perfil() != Perfil.PROFESSOR && d.perfil() != Perfil.SERVIDOR)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Perfil deve ser PROFESSOR ou SERVIDOR");
        String matricula = d.perfil().name().substring(0, 3) + "-" + Cpf.normalizar(d.cpf());
        return criar(new CriarUsuario(d.nome(), d.cpf(), d.email(), matricula, d.senha(), null, null, null),
                d.perfil());
    }

    public List<Resposta> listar(Perfil perfil) {
        return (perfil == null ? repository.findAll() : repository.findByPerfil(perfil))
                .stream().map(Resposta::de).toList();
    }

    public Resposta alterarAtivo(UUID id, boolean ativo) {
        Usuario u = repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario nao encontrado"));
        u.definirAtivo(ativo);
        return Resposta.de(repository.save(u));
    }

    private void validarEmail(String email) {
        if (!FORMATO_EMAIL.matcher(email.trim()).matches())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email invalido");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
