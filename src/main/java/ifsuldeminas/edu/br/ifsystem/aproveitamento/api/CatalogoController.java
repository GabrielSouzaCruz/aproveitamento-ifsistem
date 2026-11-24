package ifsuldeminas.edu.br.ifsystem.aproveitamento.api;

import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.CatalogoDtos.CoordenadorRequest;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.CatalogoDtos.CoordenadorResposta;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.CatalogoDtos.CursoRequest;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.CatalogoDtos.CursoResposta;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.CatalogoDtos.DisciplinaCursoRequest;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.CatalogoDtos.DisciplinaCursoResposta;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.service.CatalogoService;
import ifsuldeminas.edu.br.ifsystem.seguranca.UsuarioAutenticado;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Perfil;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** GET para qualquer usuario logado (so ativos); escrita apenas para SERVIDOR, que inativa em vez de apagar. */
@RestController
@RequestMapping("/cursos")
public class CatalogoController {
    private final CatalogoService catalogo;

    public CatalogoController(CatalogoService catalogo) {
        this.catalogo = catalogo;
    }

    @GetMapping
    public List<CursoResposta> listar() {
        return catalogo.listarCursos(ehServidor()).stream().map(CursoResposta::de).toList();
    }

    @GetMapping("/{id}")
    public CursoResposta buscar(@PathVariable UUID id) {
        CursoResposta curso = CursoResposta.de(catalogo.buscarCurso(id));
        if (!curso.ativo() && !ehServidor())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Curso nao encontrado");
        return curso;
    }

    @PostMapping
    public ResponseEntity<CursoResposta> criar(@Valid @RequestBody CursoRequest dados) {
        exigirServidor();
        CursoResposta criado = CursoResposta.de(catalogo.criarCurso(dados.codigo(), dados.nome()));
        return ResponseEntity.created(URI.create("/cursos/" + criado.id())).body(criado);
    }

    @PutMapping("/{id}")
    public CursoResposta atualizar(@PathVariable UUID id, @Valid @RequestBody CursoRequest dados) {
        exigirServidor();
        return CursoResposta.de(catalogo.atualizarCurso(id, dados.codigo(), dados.nome()));
    }

    @PostMapping("/{id}/inativar")
    public CursoResposta inativar(@PathVariable UUID id) {
        exigirServidor();
        return CursoResposta.de(catalogo.definirCursoAtivo(id, false));
    }

    @PostMapping("/{id}/ativar")
    public CursoResposta ativar(@PathVariable UUID id) {
        exigirServidor();
        return CursoResposta.de(catalogo.definirCursoAtivo(id, true));
    }

    // ---------- Disciplinas ----------

    @GetMapping("/{id}/disciplinas")
    public List<DisciplinaCursoResposta> listarDisciplinas(@PathVariable UUID id) {
        return catalogo.listarDisciplinas(id, ehServidor()).stream().map(DisciplinaCursoResposta::de).toList();
    }

    @PostMapping("/{id}/disciplinas")
    public ResponseEntity<DisciplinaCursoResposta> criarDisciplina(@PathVariable UUID id,
                                                                  @Valid @RequestBody DisciplinaCursoRequest d) {
        exigirServidor();
        DisciplinaCursoResposta criada = DisciplinaCursoResposta.de(
                catalogo.criarDisciplina(id, d.codigo(), d.nome(), d.cargaHoraria(), d.ementa()));
        return ResponseEntity.created(URI.create("/cursos/" + id + "/disciplinas/" + criada.id())).body(criada);
    }

    @PutMapping("/{id}/disciplinas/{disciplinaId}")
    public DisciplinaCursoResposta atualizarDisciplina(@PathVariable UUID id, @PathVariable UUID disciplinaId,
                                                       @Valid @RequestBody DisciplinaCursoRequest d) {
        exigirServidor();
        return DisciplinaCursoResposta.de(
                catalogo.atualizarDisciplina(id, disciplinaId, d.codigo(), d.nome(), d.cargaHoraria(), d.ementa()));
    }

    @PostMapping("/{id}/disciplinas/{disciplinaId}/inativar")
    public DisciplinaCursoResposta inativarDisciplina(@PathVariable UUID id, @PathVariable UUID disciplinaId) {
        exigirServidor();
        return DisciplinaCursoResposta.de(catalogo.definirDisciplinaAtiva(id, disciplinaId, false));
    }

    @PostMapping("/{id}/disciplinas/{disciplinaId}/ativar")
    public DisciplinaCursoResposta ativarDisciplina(@PathVariable UUID id, @PathVariable UUID disciplinaId) {
        exigirServidor();
        return DisciplinaCursoResposta.de(catalogo.definirDisciplinaAtiva(id, disciplinaId, true));
    }

    // ---------- Coordenadores ----------

    @GetMapping("/{id}/coordenadores")
    public List<CoordenadorResposta> listarCoordenadores(@PathVariable UUID id) {
        exigirServidor();
        return catalogo.listarCoordenadores(id).stream().map(CoordenadorResposta::de).toList();
    }

    @PostMapping("/{id}/coordenadores")
    public ResponseEntity<CoordenadorResposta> adicionarCoordenador(@PathVariable UUID id,
                                                                   @Valid @RequestBody CoordenadorRequest dados) {
        exigirServidor();
        CoordenadorResposta c = CoordenadorResposta.de(catalogo.adicionarCoordenador(id, dados.professorId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(c);
    }

    @DeleteMapping("/{id}/coordenadores")
    public ResponseEntity<Void> removerCoordenador(@PathVariable UUID id,
                                                   @Valid @RequestBody CoordenadorRequest dados) {
        exigirServidor();
        catalogo.removerCoordenador(id, dados.professorId());
        return ResponseEntity.noContent().build();
    }

    private static UsuarioAutenticado usuarioAtual() {
        UsuarioAutenticado u = UsuarioAutenticado.de(SecurityContextHolder.getContext().getAuthentication());
        if (u == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticacao necessaria");
        return u;
    }

    private static boolean ehServidor() {
        return usuarioAtual().perfil() == Perfil.SERVIDOR;
    }

    private static void exigirServidor() {
        if (!ehServidor())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas servidor administra o catalogo");
    }
}
