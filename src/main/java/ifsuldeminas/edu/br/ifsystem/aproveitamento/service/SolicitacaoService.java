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
import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.AproveitamentoDtos.AtualizarSolicitacao;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Perfil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Criacao e consultas de solicitacoes. Regras de perfil ficam no controller. */
@Service
public class SolicitacaoService {
    private static final Set<StatusSolicitacao> STATUS_EDITAVEIS =
            EnumSet.of(StatusSolicitacao.RASCUNHO, StatusSolicitacao.PENDENTE);

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
        DisciplinaCurso destino = destinoAtivo(d.disciplinaDestinoId());
        // Texto (nome, curso, carga horaria) copiado do catalogo: preserva o historico se o catalogo mudar.
        Solicitacao solicitacao = solicitacoes.save(new Solicitacao(d.alunoId(), destino));
        for (DisciplinaCursadaEntrada e : d.disciplinas()) {
            disciplinas.save(new DisciplinaCursada(solicitacao, e.nome(), e.instituicao(), e.cargaHoraria(),
                    e.ementa()));
        }
        historicos.save(new HistoricoStatus(solicitacao, null, StatusSolicitacao.RASCUNHO, d.alunoId(), null));
        return solicitacao;
    }

    /**
     * Altera o destino e substitui as disciplinas cursadas. So RASCUNHO e PENDENTE aceitam edicao (409);
     * quem chama garante que o usuario e o aluno dono.
     */
    @Transactional
    public Solicitacao atualizar(UUID id, AtualizarSolicitacao d) {
        Solicitacao s = buscar(id);
        if (!STATUS_EDITAVEIS.contains(s.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solicitacao so pode ser editada em RASCUNHO ou PENDENTE (status atual: " + s.getStatus() + ")");
        s.alterarDestino(destinoAtivo(d.disciplinaDestinoId()));
        disciplinas.deleteAll(disciplinas.findBySolicitacaoId(id));
        disciplinas.flush();
        for (DisciplinaCursadaEntrada e : d.disciplinas()) {
            disciplinas.save(new DisciplinaCursada(s, e.nome(), e.instituicao(), e.cargaHoraria(), e.ementa()));
        }
        return solicitacoes.saveAndFlush(s);
    }

    /**
     * Cria um RASCUNHO a partir de uma solicitacao INDEFERIDA: copia destino e disciplinas cursadas (com ementa),
     * sem anexos. Uma unica nova tentativa por indeferimento; o aluno dono e exigido (403).
     */
    @Transactional
    public Solicitacao novaTentativa(UUID origemId, UUID alunoId) {
        Solicitacao origem = buscar(origemId);
        if (!origem.getAlunoId().equals(alunoId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solicitacao pertence a outro aluno");
        if (origem.getStatus() != StatusSolicitacao.INDEFERIDA)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Nova tentativa so a partir de solicitacao INDEFERIDA (status atual: " + origem.getStatus() + ")");
        solicitacoes.findBySolicitacaoOrigemId(origemId).ifPresent(e -> {
            throw new NovaTentativaJaExisteException(e.getId());
        });
        Solicitacao nova = solicitacoes.save(Solicitacao.novaTentativa(origem));
        for (DisciplinaCursada c : disciplinas.findBySolicitacaoId(origemId)) {
            disciplinas.save(new DisciplinaCursada(nova, c.getNome(), c.getInstituicao(), c.getCargaHoraria(),
                    c.getEmenta()));
        }
        historicos.save(new HistoricoStatus(nova, null, StatusSolicitacao.RASCUNHO, alunoId,
                "Nova tentativa de #" + origemId.toString().substring(0, 8)));
        return nova;
    }

    private DisciplinaCurso destinoAtivo(UUID disciplinaDestinoId) {
        return catalogo.findById(disciplinaDestinoId)
                .filter(x -> x.isAtivo() && x.getCurso().isAtivo())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "disciplinaDestinoId inexistente ou inativa"));
    }

    /** {@code podeDecidir}: o usuario e professor coordenador do curso da solicitacao. */
    @Transactional(readOnly = true)
    public SolicitacaoDetalhe detalhar(UUID id, UUID usuarioId, Perfil perfil) {
        Solicitacao s = buscar(id);
        boolean podeDecidir = perfil == Perfil.PROFESSOR && coordenadores.coordena(usuarioId, s);
        Solicitacao origem = s.getSolicitacaoOrigem();
        UUID origemId = origem == null ? null : origem.getId();
        String motivo = origemId == null ? null : historicos.findBySolicitacaoIdOrderByCriadoEmAsc(origemId).stream()
                .filter(h -> h.getStatusNovo() == StatusSolicitacao.INDEFERIDA)
                .reduce((a, b) -> b).map(HistoricoStatus::getObservacao).orElse(null);
        UUID novaId = solicitacoes.findBySolicitacaoOrigemId(id).map(Solicitacao::getId).orElse(null);
        return SolicitacaoDetalhe.de(s,
                disciplinas.findBySolicitacaoId(id),
                anexos.findBySolicitacaoId(id),
                preDiagnosticos.findBySolicitacaoId(id).orElse(null),
                podeDecidir, perfil != Perfil.ALUNO)
                .comOrigem(origemId, novaId, motivo);
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
