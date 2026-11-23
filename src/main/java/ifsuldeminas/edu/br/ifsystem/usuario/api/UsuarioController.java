package ifsuldeminas.edu.br.ifsystem.usuario.api;

import ifsuldeminas.edu.br.ifsystem.usuario.api.UsuarioDtos.AlterarAtivo;
import ifsuldeminas.edu.br.ifsystem.usuario.api.UsuarioDtos.CriarPorSecretaria;
import ifsuldeminas.edu.br.ifsystem.usuario.api.UsuarioDtos.Resposta;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.usuario.service.UsuarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** Gestao de usuarios pela secretaria. O acesso (somente SERVIDOR) e imposto no SecurityConfig. */
@RestController
@RequestMapping("/usuarios")
public class UsuarioController {
    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Resposta> criar(@RequestBody CriarPorSecretaria dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Resposta.de(service.criarPorSecretaria(dados)));
    }

    @GetMapping
    public List<Resposta> listar(@RequestParam(required = false) Perfil perfil) {
        return service.listar(perfil);
    }

    @PatchMapping("/{id}/ativo")
    public Resposta alterarAtivo(@PathVariable UUID id, @RequestBody AlterarAtivo dados) {
        if (dados == null || dados.ativo() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo ativo obrigatorio");
        return service.alterarAtivo(id, dados.ativo());
    }
}
