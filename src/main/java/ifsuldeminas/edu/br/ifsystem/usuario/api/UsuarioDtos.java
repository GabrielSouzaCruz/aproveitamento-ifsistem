package ifsuldeminas.edu.br.ifsystem.usuario.api;

import ifsuldeminas.edu.br.ifsystem.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Usuario;

import java.util.UUID;

public final class UsuarioDtos {
    private UsuarioDtos() {
    }

    /** Dados internos de criacao (cadastro, secretaria e bootstrap). */
    public record CriarUsuario(
            String nome, String cpf, String email, String matricula,
            String senha, String telefone, String curso, Integer periodo) {
    }

    /** POST /usuarios (secretaria): apenas PROFESSOR ou SERVIDOR. */
    public record CriarPorSecretaria(String cpf, String nome, String email, String senha, Perfil perfil) {
    }

    public record AlterarAtivo(Boolean ativo) {
    }

    public record Resposta(UUID id, String nome, String cpf, String email, String matricula,
                           Perfil perfil, boolean ativo) {
        public static Resposta de(Usuario u) {
            return new Resposta(u.getId(), u.getNome(), u.getCpf(), u.getEmail(), u.getMatricula(),
                    u.getPerfil(), u.isAtivo());
        }
    }
}
