package ifsuldeminas.edu.br.ifsystem.auth.service;

import ifsuldeminas.edu.br.ifsystem.auth.api.AuthDtos.Cadastro;
import ifsuldeminas.edu.br.ifsystem.auth.api.AuthDtos.Login;
import ifsuldeminas.edu.br.ifsystem.auth.api.AuthDtos.LoginResposta;
import ifsuldeminas.edu.br.ifsystem.auth.api.AuthDtos.UsuarioLogado;
import ifsuldeminas.edu.br.ifsystem.usuario.api.UsuarioDtos.CriarUsuario;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Usuario;
import ifsuldeminas.edu.br.ifsystem.usuario.service.UsuarioService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final UsuarioService usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    /** Hash descartavel: mantem o custo do BCrypt quando o CPF nao existe. */
    private final String hashFalso;

    public AuthService(UsuarioService usuarios, PasswordEncoder encoder, JwtService jwt) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.jwt = jwt;
        this.hashFalso = encoder.encode("senha-inexistente");
    }

    /** Autocadastro: sempre cria um ALUNO. */
    public Usuario cadastrar(Cadastro c) {
        if (c == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados obrigatorios ausentes");
        return usuarios.criar(new CriarUsuario(c.nome(), c.cpf(), c.email(), c.matricula(), c.senha(),
                null, null, null), Perfil.ALUNO);
    }

    public LoginResposta login(Login l) {
        if (l == null || l.cpf() == null || l.senha() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CPF e senha sao obrigatorios");
        Usuario usuario = usuarios.buscarPorCpf(l.cpf()).orElse(null);
        boolean senhaOk = encoder.matches(l.senha(), usuario != null ? usuario.getSenhaHash() : hashFalso);
        if (usuario == null || !senhaOk || !usuario.isAtivo())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais invalidas");
        var emitido = jwt.gerar(usuario);
        return new LoginResposta(emitido.token(), emitido.expiraEm(),
                new UsuarioLogado(usuario.getId(), usuario.getNome(), usuario.getPerfil()));
    }
}
