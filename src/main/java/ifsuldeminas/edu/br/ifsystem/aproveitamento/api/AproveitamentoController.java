package ifsuldeminas.edu.br.ifsystem.aproveitamento.api;

import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.AproveitamentoDtos.AcaoRequest;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.AproveitamentoDtos.AnexoResposta;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.AproveitamentoDtos.CriarSolicitacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.AproveitamentoDtos.HistoricoItem;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.AproveitamentoDtos.ParecerIaResposta;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.AproveitamentoDtos.SolicitacaoDetalhe;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.AproveitamentoDtos.SolicitacaoResumo;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.Anexo;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.AnexoConteudo;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.Solicitacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.StatusSolicitacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.service.AnexoService;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.service.FluxoStatusService;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.service.NovaTentativaJaExisteException;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.service.ParecerIaService;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.service.SolicitacaoService;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.service.VerificacaoAnexoService;
import ifsuldeminas.edu.br.ifsystem.auth.service.UsuarioAutenticado;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Perfil;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.api.AproveitamentoDtos.AtualizarSolicitacao;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/aproveitamentos")
public class AproveitamentoController {
    private final SolicitacaoService solicitacoes;
    private final FluxoStatusService fluxo;
    private final AnexoService anexos;
    private final ParecerIaService parecerIa;
    private final VerificacaoAnexoService verificacao;

    public AproveitamentoController(SolicitacaoService solicitacoes, FluxoStatusService fluxo,
                                    AnexoService anexos, ParecerIaService parecerIa,
                                    VerificacaoAnexoService verificacao) {
        this.verificacao = verificacao;
        this.solicitacoes = solicitacoes;
        this.fluxo = fluxo;
        this.anexos = anexos;
        this.parecerIa = parecerIa;
    }

    @PostMapping
    public ResponseEntity<SolicitacaoResumo> criar(@Valid @RequestBody CriarSolicitacao dados) {
        Usuario usuario = usuarioAtual();
        if (usuario.perfil() != Perfil.ALUNO)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas aluno pode criar solicitacao");
        if (!usuario.id().equals(dados.alunoId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solicitacao pertence a outro aluno");

        Solicitacao criada = solicitacoes.criar(dados);
        return ResponseEntity.created(URI.create("/aproveitamentos/" + criada.getId()))
                .body(SolicitacaoResumo.de(criada));
    }

    @GetMapping("/{id}")
    public SolicitacaoDetalhe detalhar(@PathVariable UUID id) {
        Usuario usuario = usuarioAtual();
        exigirDonoSeAluno(usuario, solicitacoes.buscar(id).getAlunoId());
        return solicitacoes.detalhar(id, usuario.id(), usuario.perfil());
    }

    @PutMapping("/{id}")
    public SolicitacaoResumo atualizar(@PathVariable UUID id, @Valid @RequestBody AtualizarSolicitacao dados) {
        Usuario usuario = usuarioAtual();
        if (usuario.perfil() != Perfil.ALUNO)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas o aluno dono pode editar a solicitacao");
        exigirDonoSeAluno(usuario, solicitacoes.buscar(id).getAlunoId());
        return SolicitacaoResumo.de(solicitacoes.atualizar(id, dados));
    }

    @GetMapping
    public List<SolicitacaoResumo> listar(@RequestParam(required = false) StatusSolicitacao status,
                                          @RequestParam(required = false) UUID alunoId,
                                          @RequestParam(required = false) UUID cursoId) {
        Usuario usuario = usuarioAtual();
        UUID filtroAluno = usuario.perfil() == Perfil.ALUNO ? usuario.id() : alunoId;
        // Professor so enxerga solicitacoes dos cursos que coordena.
        UUID filtroProfessor = usuario.perfil() == Perfil.PROFESSOR ? usuario.id() : null;
        return solicitacoes.listar(status, filtroAluno, cursoId, filtroProfessor);
    }

    @GetMapping("/{id}/historico")
    public List<HistoricoItem> historico(@PathVariable UUID id) {
        Usuario usuario = usuarioAtual();
        exigirDonoSeAluno(usuario, solicitacoes.buscar(id).getAlunoId());
        return solicitacoes.historico(id);
    }

    @GetMapping("/{id}/parecer-ia")
    public ParecerIaResposta parecerIa(@PathVariable UUID id) {
        Usuario usuario = usuarioAtual();
        if (usuario.perfil() == Perfil.ALUNO)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Aluno nao acessa o parecer da IA");
        solicitacoes.buscar(id);
        return parecerIa.maisRecente(id).map(ParecerIaResposta::de)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parecer da IA nao encontrado"));
    }

    @PostMapping("/{id}/parecer-ia/regerar")
    public ResponseEntity<Void> regerarParecerIa(@PathVariable UUID id) {
        Usuario usuario = usuarioAtual();
        solicitacoes.buscar(id);
        if (!parecerIa.podeRegerar(usuario.id(), usuario.perfil(), id))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Perfil sem permissao para regerar o parecer");
        parecerIa.regerar(id);
        return ResponseEntity.accepted().build();
    }

    /** Aluno dono, a partir de INDEFERIDA: cria um novo RASCUNHO com os mesmos dados (sem anexos). */
    @PostMapping("/{id}/nova-tentativa")
    public ResponseEntity<SolicitacaoResumo> novaTentativa(@PathVariable UUID id) {
        Usuario usuario = usuarioAtual();
        if (usuario.perfil() != Perfil.ALUNO)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas o aluno dono pode solicitar novamente");
        Solicitacao criada = solicitacoes.novaTentativa(id, usuario.id());
        return ResponseEntity.created(URI.create("/aproveitamentos/" + criada.getId()))
                .body(SolicitacaoResumo.de(criada));
    }

    @ExceptionHandler(NovaTentativaJaExisteException.class)
    public ResponseEntity<Map<String, Object>> novaTentativaJaExiste(NovaTentativaJaExisteException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("erro", e.getMessage(), "novaTentativaId", e.getNovaTentativaId()));
    }

    @PostMapping("/{id}/{acao}")
    public SolicitacaoResumo executar(@PathVariable UUID id, @PathVariable String acao,
                                      @Valid @RequestBody(required = false) AcaoRequest corpo) {
        Usuario usuario = usuarioAtual();
        String observacao = corpo == null ? null : corpo.observacao();
        return SolicitacaoResumo.de(fluxo.executar(id, acao, usuario.id(), usuario.perfil(), observacao));
    }

    @PostMapping(path = "/{id}/anexos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AnexoResposta> anexar(@PathVariable UUID id,
                                                @RequestPart("arquivo") MultipartFile arquivo) {
        exigirAlunoDono(usuarioAtual(), id);
        Anexo anexo = anexos.anexar(id, arquivo);
        return ResponseEntity.created(URI.create("/aproveitamentos/" + id + "/anexos/" + anexo.getId()))
                .body(AnexoResposta.de(anexo));
    }

    @GetMapping("/{id}/anexos/{anexoId}")
    public ResponseEntity<byte[]> baixarAnexo(@PathVariable UUID id, @PathVariable UUID anexoId) {
        Usuario usuario = usuarioAtual();
        if (usuario.perfil() == Perfil.ALUNO)
            exigirDonoSeAluno(usuario, solicitacoes.buscar(id).getAlunoId());
        else if (usuario.perfil() != Perfil.SERVIDOR && usuario.perfil() != Perfil.PROFESSOR)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Perfil sem acesso aos anexos");

        AnexoConteudo conteudo = anexos.baixar(id, anexoId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(conteudo.getAnexo().getNomeArquivo()))
                .body(conteudo.getConteudo());
    }

    @DeleteMapping("/{id}/anexos/{anexoId}")
    public ResponseEntity<Void> removerAnexo(@PathVariable UUID id, @PathVariable UUID anexoId) {
        exigirAlunoDono(usuarioAtual(), id);
        anexos.remover(id, anexoId);
        return ResponseEntity.noContent().build();
    }

    /** SERVIDOR ou professor coordenador do curso: refaz a verificacao em segundo plano (202). */
    @PostMapping("/{id}/anexos/{anexoId}/reverificar")
    public ResponseEntity<AnexoResposta> reverificarAnexo(@PathVariable UUID id, @PathVariable UUID anexoId) {
        Usuario usuario = usuarioAtual();
        solicitacoes.buscar(id);
        if (!verificacao.podeReverificar(usuario.id(), usuario.perfil(), id))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Perfil sem permissao para reverificar o anexo");
        return ResponseEntity.accepted().body(AnexoResposta.de(verificacao.reverificar(id, anexoId), true));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> uploadMuitoGrande(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("erro", "Arquivo maior que o limite permitido (5 MB)"));
    }

    private static String contentDisposition(String nomeArquivo) {
        ContentDisposition.Builder disposition = ContentDisposition.attachment();
        boolean ascii = StandardCharsets.US_ASCII.newEncoder().canEncode(nomeArquivo);
        return (ascii ? disposition.filename(nomeArquivo) : disposition.filename(nomeArquivo, StandardCharsets.UTF_8))
                .build().toString();
    }

    /** So o aluno dono da solicitacao; 404 se ela nao existir. */
    private void exigirAlunoDono(Usuario usuario, UUID solicitacaoId) {
        if (usuario.perfil() != Perfil.ALUNO)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas o aluno dono pode alterar anexos");
        exigirDonoSeAluno(usuario, solicitacoes.buscar(solicitacaoId).getAlunoId());
    }

    private static void exigirDonoSeAluno(Usuario usuario, UUID alunoId) {
        if (usuario.perfil() == Perfil.ALUNO && !usuario.id().equals(alunoId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solicitacao pertence a outro aluno");
    }

    /** Usuario autenticado, colocado no SecurityContext pelo JwtAuthFilter. */
    private Usuario usuarioAtual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioAutenticado u))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticacao necessaria");
        return new Usuario(u.id(), u.perfil());
    }

    private record Usuario(UUID id, Perfil perfil) {
    }
}
