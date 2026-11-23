package ifsuldeminas.edu.br.ifsystem.aproveitamento.service;

import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.Coordenador;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.Curso;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.DisciplinaCurso;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.Solicitacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.CoordenadorRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.CursoRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.DisciplinaCursoRepository;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.usuario.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Catalogo de cursos/disciplinas e coordenadores. Regras de perfil ficam no controller. */
@Service
public class CatalogoService {
    private final CursoRepository cursos;
    private final DisciplinaCursoRepository disciplinas;
    private final CoordenadorRepository coordenadores;
    private final UsuarioRepository usuarios;

    public CatalogoService(CursoRepository cursos, DisciplinaCursoRepository disciplinas,
                           CoordenadorRepository coordenadores, UsuarioRepository usuarios) {
        this.cursos = cursos;
        this.disciplinas = disciplinas;
        this.coordenadores = coordenadores;
        this.usuarios = usuarios;
    }

    // ---------- Cursos ----------

    @Transactional(readOnly = true)
    public List<Curso> listarCursos(boolean incluirInativos) {
        return incluirInativos
                ? cursos.findAll().stream().sorted(Comparator.comparing(Curso::getNome)).toList()
                : cursos.findByAtivoTrueOrderByNome();
    }

    @Transactional(readOnly = true)
    public Curso buscarCurso(UUID id) {
        return cursos.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Curso nao encontrado"));
    }

    @Transactional
    public Curso criarCurso(String codigo, String nome) {
        String cod = codigo.trim();
        if (cursos.existsByCodigo(cod))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ja existe curso com este codigo");
        return cursos.save(new Curso(cod, nome.trim()));
    }

    @Transactional
    public Curso atualizarCurso(UUID id, String codigo, String nome) {
        Curso curso = buscarCurso(id);
        String cod = codigo.trim();
        if (cursos.existsByCodigoAndIdNot(cod, id))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ja existe curso com este codigo");
        curso.atualizar(cod, nome.trim());
        return curso;
    }

    @Transactional
    public Curso definirCursoAtivo(UUID id, boolean ativo) {
        Curso curso = buscarCurso(id);
        curso.definirAtivo(ativo);
        return curso;
    }

    // ---------- Disciplinas ----------

    /** Usuario comum so enxerga curso e disciplinas ativos; o servidor enxerga tudo. */
    @Transactional(readOnly = true)
    public List<DisciplinaCurso> listarDisciplinas(UUID cursoId, boolean incluirInativas) {
        Curso curso = buscarCurso(cursoId);
        if (!incluirInativas && !curso.isAtivo())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Curso nao encontrado");
        return incluirInativas ? disciplinas.findByCursoIdOrderByNome(cursoId)
                : disciplinas.findByCursoIdAndAtivoTrueOrderByNome(cursoId);
    }

    @Transactional
    public DisciplinaCurso criarDisciplina(UUID cursoId, String codigo, String nome, Integer cargaHoraria,
                                           String ementa) {
        Curso curso = buscarCurso(cursoId);
        String cod = codigo.trim();
        if (disciplinas.existsByCursoIdAndCodigo(cursoId, cod))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ja existe disciplina com este codigo no curso");
        return disciplinas.save(new DisciplinaCurso(curso, cod, nome.trim(), cargaHoraria, ementa.trim()));
    }

    @Transactional
    public DisciplinaCurso atualizarDisciplina(UUID cursoId, UUID id, String codigo, String nome,
                                               Integer cargaHoraria, String ementa) {
        DisciplinaCurso d = buscarDisciplina(cursoId, id);
        String cod = codigo.trim();
        if (disciplinas.existsByCursoIdAndCodigoAndIdNot(cursoId, cod, id))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ja existe disciplina com este codigo no curso");
        d.atualizar(cod, nome.trim(), cargaHoraria, ementa.trim());
        return d;
    }

    @Transactional
    public DisciplinaCurso definirDisciplinaAtiva(UUID cursoId, UUID id, boolean ativo) {
        DisciplinaCurso d = buscarDisciplina(cursoId, id);
        d.definirAtivo(ativo);
        return d;
    }

    private DisciplinaCurso buscarDisciplina(UUID cursoId, UUID id) {
        return disciplinas.findByIdAndCursoId(id, cursoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Disciplina nao encontrada"));
    }

    // ---------- Coordenadores ----------

    @Transactional
    public Coordenador adicionarCoordenador(UUID cursoId, UUID professorId) {
        Curso curso = buscarCurso(cursoId);
        var professor = usuarios.findById(professorId)
                .filter(u -> u.getPerfil() == Perfil.PROFESSOR && u.isAtivo())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "professorId deve ser de um PROFESSOR ativo"));
        if (coordenadores.existsByProfessorIdAndCursoId(professor.getId(), cursoId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Professor ja coordena este curso");
        return coordenadores.save(new Coordenador(professor.getId(), curso));
    }

    @Transactional
    public void removerCoordenador(UUID cursoId, UUID professorId) {
        buscarCurso(cursoId);
        Coordenador c = coordenadores.findByProfessorIdAndCursoId(professorId, cursoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coordenador nao encontrado"));
        coordenadores.delete(c);
    }

    /** Professor coordena o curso da solicitacao (por FK; por nome so nas solicitacoes antigas, sem catalogo). */
    @Transactional(readOnly = true)
    public boolean coordena(UUID professorId, Solicitacao solicitacao) {
        DisciplinaCurso destino = solicitacao.getDisciplinaCursoDestino();
        return destino != null
                ? coordenadores.existsByProfessorIdAndCursoId(professorId, destino.getCurso().getId())
                : coordenadores.existsByProfessorIdAndCursoNome(professorId, solicitacao.getCurso());
    }
}
