package ifsuldeminas.edu.br.ifsystem.auth.api;

import ifsuldeminas.edu.br.ifsystem.usuario.domain.Perfil;

import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record Cadastro(String cpf, String nome, String email, String matricula, String senha) {
    }

    public record Login(String cpf, String senha) {
    }

    public record UsuarioLogado(UUID id, String nome, Perfil perfil) {
    }

    public record LoginResposta(String token, Instant expiraEm, UsuarioLogado usuario) {
    }
}
