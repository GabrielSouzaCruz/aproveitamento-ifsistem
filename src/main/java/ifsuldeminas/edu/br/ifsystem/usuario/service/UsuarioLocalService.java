package ifsuldeminas.edu.br.ifsystem.usuario.service;

import ifsuldeminas.edu.br.ifsystem.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Usuario;
import ifsuldeminas.edu.br.ifsystem.usuario.repository.UsuarioRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/** Mantem a copia local (public.usuarios) dos usuarios do ifsistem-auth. So leitura para o resto do sistema. */
@Service
public class UsuarioLocalService {
    private final UsuarioRepository repository;
    private final TransactionTemplate tx;

    public UsuarioLocalService(UsuarioRepository repository, TransactionTemplate tx) {
        this.repository = repository;
        this.tx = tx;
    }

    /** Upsert por id. Se duas requisicoes inserem o mesmo id ao mesmo tempo, a perdedora repete como update. */
    public void gravar(UUID id, String nome, String cpf, Perfil perfil, boolean ativo) {
        try {
            tx.executeWithoutResult(s -> aplicar(id, nome, cpf, perfil, ativo));
        } catch (DataIntegrityViolationException concorrente) {
            tx.executeWithoutResult(s -> aplicar(id, nome, cpf, perfil, ativo));
        }
    }

    private void aplicar(UUID id, String nome, String cpf, Perfil perfil, boolean ativo) {
        Usuario u = repository.findById(id).orElseGet(() -> new Usuario(id));
        u.sincronizar(nome, cpf, perfil, ativo);
        repository.saveAndFlush(u);
    }
}
