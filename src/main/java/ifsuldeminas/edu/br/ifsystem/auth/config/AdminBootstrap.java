package ifsuldeminas.edu.br.ifsystem.auth.config;

import ifsuldeminas.edu.br.ifsystem.usuario.api.UsuarioDtos.CriarUsuario;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Cpf;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.usuario.repository.UsuarioRepository;
import ifsuldeminas.edu.br.ifsystem.usuario.service.UsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Cria o SERVIDOR de ADMIN_CPF/ADMIN_SENHA se nao existir usuario com esse CPF (mesmo havendo outros servidores). */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminBootstrap implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UsuarioRepository repository;
    private final UsuarioService usuarios;
    private final String cpf;
    private final String senha;
    private final String email;

    public AdminBootstrap(UsuarioRepository repository, UsuarioService usuarios,
                          @Value("${app.admin.cpf:}") String cpf,
                          @Value("${app.admin.senha:}") String senha,
                          @Value("${app.admin.email:secretaria@ifsuldeminas.edu.br}") String email) {
        this.repository = repository;
        this.usuarios = usuarios;
        this.cpf = cpf;
        this.senha = senha;
        this.email = email;
    }

    @Override
    public void run(String... args) {
        if (cpf.isBlank() || senha.isBlank() || repository.existsByCpf(Cpf.normalizar(cpf)))
            return;
        usuarios.criar(new CriarUsuario("Secretaria", cpf, email, "SECRETARIA", senha, null, null, null),
                Perfil.SERVIDOR);
        log.info("Conta da secretaria criada a partir de ADMIN_CPF");
    }
}
