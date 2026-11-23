package ifsuldeminas.edu.br.ifsystem.auth;

import ifsuldeminas.edu.br.ifsystem.auth.config.AdminBootstrap;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.usuario.repository.UsuarioRepository;
import ifsuldeminas.edu.br.ifsystem.usuario.service.UsuarioService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminBootstrapTest {
    private final UsuarioRepository repo = mock(UsuarioRepository.class);
    private final UsuarioService usuarios = mock(UsuarioService.class);

    private AdminBootstrap com(String cpf, String senha) {
        return new AdminBootstrap(repo, usuarios, cpf, senha, "secretaria@ifsuldeminas.edu.br");
    }

    @Test
    void criaSecretariaQuandoNaoHaServidorEVariaveisExistem() {
        when(repo.existsByPerfil(Perfil.SERVIDOR)).thenReturn(false);
        com("52998224725", "senha-forte-1").run();
        verify(usuarios).criar(any(), eq(Perfil.SERVIDOR));
    }

    @Test
    void naoFazNadaSeJaExisteServidor() {
        when(repo.existsByPerfil(Perfil.SERVIDOR)).thenReturn(true);
        com("52998224725", "senha-forte-1").run();
        verify(usuarios, never()).criar(any(), any());
    }

    @Test
    void naoFazNadaSemVariaveis() {
        com("", "").run();
        verify(usuarios, never()).criar(any(), any());
    }
}
