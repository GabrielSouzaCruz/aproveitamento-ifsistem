package ifsuldeminas.edu.br.ifsystem.aproveitamento.service;

import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.AproveitamentoDtos.CriarSolicitacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.AproveitamentoDtos.DisciplinaCursadaEntrada;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.AproveitamentoDtos.HistoricoItem;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.AproveitamentoDtos.SolicitacaoDetalhe;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.AproveitamentoDtos.SolicitacaoResumo;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.DisciplinaCursada;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.DisciplinaCurso;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.HistoricoStatus;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.DisciplinaCursoRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.Solicitacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.StatusSolicitacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.AnexoRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.DisciplinaCursadaRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.HistoricoStatusRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.PreDiagnosticoRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.SolicitacaoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** Criacao e consultas de solicitacoes. Regras de perfil ficam no controller. */
@Service
public class SolicitacaoService {
    private final SolicitacaoRepository solicitacoes;
    private final DisciplinaCursadaRepository disciplinas;
    private final AnexoRepository anexos;
    private final PreDiagnosticoRepository preDiagnosticos;
    private final HistoricoStatusRepository historicos;
    private final DisciplinaCursoRepository catalogo;
    private final CatalogoService coordenadores;

    public SolicitacaoService(SolicitacaoRepository solicitacoes, DisciplinaCursadaRepository disciplinas,
                              AnexoRepository anexos, PreDiagnosticoRepository preDiagnosticos,
                              HistoricoStatusRepository historicos, DisciplinaCursoRepository catalogo,
                              CatalogoService coordenadores) {
        this.catalogo = catalogo;
        this.coordenadores = coordenadores;
        this.solicitacoes = solicitacoes;
        this.disciplinas = disciplinas;
        this.anexos = anexos;
        this.preDiagnosticos = preDiagnosticos;
        this.historicos = historicos;
    }

    @Transactional
    public Solicitacao criar(CriarSolicitacao d) {
        DisciplinaCurso destino = catalogo.findById(d.disciplinaDestinoId())
                .filter(x -> x.isAtivo() && x.getCurso().isAtivo())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "disciplinaDestinoId inexistente ou inativa"));
        // Texto (nome, curso, carga horaria) copiado do catalogo: preserva o historico se o catalogo mudar.
        Solicitacao solicitacao = solicitacoes.save(new Solicitacao(d.alunoId(), destino));
        for (DisciplinaCursadaEntrada e : d.disciplinas()) {
            disciplinas.save(new DisciplinaCursada(solicitacao, e.nome(), e.instituicao(), e.cargaHoraria(),
                    e.ementa()));
        }
        historicos.save(new HistoricoStatus(solicitacao, null, StatusSolicitacao.RASCUNHO, d.alunoId(), null));
        return solicitacao;
    }

    @Transactional(readOnly = true)
    public SolicitacaoDetalhe detalhar(UUID id) {
        Solicitacao s = buscar(id);
        return SolicitacaoDetalhe.de(s,
                disciplinas.findBySolicitacaoId(id),
                anexos.findBySolicitacaoId(id),
                preDiagnosticos.findBySolicitacaoId(id).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<HistoricoItem> historico(UUID id) {
        buscar(id);
        return historicos.findBySolicitacaoIdOrderByCriadoEmAsc(id).stream().map(HistoricoItem::de).toList();
    }

    /**
     * Lista com filtros opcionais. {@code professorId}, quando informado, restringe as solicitacoes aos
     * cursos que o professor coordena.
     */
    @Transactional(readOnly = true)
    public List<SolicitacaoResumo> listar(StatusSolicitacao status, UUID alunoId, UUID cursoId, UUID professorId) {
        List<Solicitacao> encontradas;
        if (alunoId != null)
            encontradas = solicitacoes.findByAlunoId(alunoId);
        else if (status != null)
            encontradas = solicitacoes.findByStatus(status);
        else
            encontradas = solicitacoes.findAll();
        return encontradas.stream()
                .filter(s -> status == null || s.getStatus() == status)
                .filter(s -> cursoId == null || (s.getDisciplinaCursoDestino() != null
                        && cursoId.equals(s.getDisciplinaCursoDestino().getCurso().getId())))
                .filter(s -> professorId == null || coordenadores.coordena(professorId, s))
                .map(SolicitacaoResumo::de).toList();
    }

    /** Consulta leve da solicitacao (sem filhos); 404 se nao existir. */
    @Transactional(readOnly = true)
    public Solicitacao buscar(UUID id) {
        return solicitacoes.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitacao nao encontrada"));
    }
}
