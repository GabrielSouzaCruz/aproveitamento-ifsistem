package ifsuldeminas.edu.br.ifsystem.aproveitamento.service;

import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.Anexo;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.AnexoConteudo;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.DisciplinaCursada;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.StatusVerificacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.TipoDetectado;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.AnexoConteudoRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.AnexoRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.DisciplinaCursadaRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.SolicitacaoRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.service.VerificadorAnexo.EntradaVerificacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.service.VerificadorAnexo.ResultadoVerificacao;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Perfil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** Verifica os anexos fora da transacao do upload; falhas viram PRECISA_CONFERIR, nunca excecao. */
@Service
public class VerificacaoAnexoService {
    private static final Logger log = LoggerFactory.getLogger(VerificacaoAnexoService.class);
    static final String MOTIVO_FALHA = "Falha na verificação automática; conferir manualmente.";

    private final AnexoRepository anexos;
    private final AnexoConteudoRepository conteudos;
    private final SolicitacaoRepository solicitacoes;
    private final DisciplinaCursadaRepository disciplinas;
    private final VerificadorAnexo verificador;
    private final CatalogoService coordenadores;
    private final ApplicationEventPublisher eventos;

    public VerificacaoAnexoService(AnexoRepository anexos, AnexoConteudoRepository conteudos,
                                   SolicitacaoRepository solicitacoes, DisciplinaCursadaRepository disciplinas,
                                   VerificadorAnexo verificador, CatalogoService coordenadores,
                                   ApplicationEventPublisher eventos) {
        this.anexos = anexos;
        this.conteudos = conteudos;
        this.solicitacoes = solicitacoes;
        this.disciplinas = disciplinas;
        this.verificador = verificador;
        this.coordenadores = coordenadores;
        this.eventos = eventos;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoAnexar(AnexoParaVerificarEvent evento) {
        verificar(evento.anexoId());
    }

    /** Volta o anexo para VERIFICANDO e agenda a verificacao para depois do commit. */
    @Transactional
    public Anexo reverificar(UUID solicitacaoId, UUID anexoId) {
        Anexo anexo = anexos.findByIdAndSolicitacaoId(anexoId, solicitacaoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Anexo nao encontrado"));
        anexo.marcarVerificando();
        anexos.save(anexo);
        eventos.publishEvent(new AnexoParaVerificarEvent(anexo.getId()));
        return anexo;
    }

    /** Reverificacao liberada para SERVIDOR e para o professor que coordena o curso da solicitacao. */
    @Transactional(readOnly = true)
    public boolean podeReverificar(UUID usuarioId, Perfil perfil, UUID solicitacaoId) {
        if (perfil == Perfil.SERVIDOR)
            return true;
        return perfil == Perfil.PROFESSOR && solicitacoes.findById(solicitacaoId)
                .map(s -> coordenadores.coordena(usuarioId, s)).orElse(false);
    }

    /** Nunca propaga excecao: quem chama (listener assincrono) nao tem a quem repassar. */
    void verificar(UUID anexoId) {
        Anexo anexo = null;
        try {
            anexo = anexos.findById(anexoId).orElse(null);
            if (anexo == null) {
                log.warn("Verificacao ignorada: anexo {} nao encontrado (removido?)", anexoId);
                return;
            }
            byte[] pdf = conteudos.findById(anexoId).map(AnexoConteudo::getConteudo).orElse(null);
            ResultadoVerificacao r = verificador.verificar(montarEntrada(anexo, pdf));
            if (r.textoTranscrito() != null && !r.textoTranscrito().isBlank())
                anexo.setTextoExtraido(r.textoTranscrito());
            anexo.registrarVerificacao(r.status(), r.tipoDetectado(), r.motivo(), r.modelo());
            anexos.save(anexo);
        } catch (Exception e) {
            log.error("Falha ao verificar o anexo {}", anexoId, e);
            registrarFalha(anexo);
        }
    }

    private void registrarFalha(Anexo anexo) {
        if (anexo == null)
            return;
        try {
            anexo.registrarVerificacao(StatusVerificacao.PRECISA_CONFERIR, TipoDetectado.OUTRO, MOTIVO_FALHA, null);
            anexos.save(anexo);
        } catch (Exception e) {
            log.error("Nao foi possivel registrar a falha da verificacao do anexo {}", anexo.getId(), e);
        }
    }

    private EntradaVerificacao montarEntrada(Anexo anexo, byte[] pdf) {
        List<DisciplinaCursada> cursadas = disciplinas.findBySolicitacaoId(anexo.getSolicitacao().getId());
        DisciplinaCursada origem = cursadas.isEmpty() ? null : cursadas.get(0);
        return new EntradaVerificacao(
                origem == null ? null : origem.getNome(), origem == null ? null : origem.getInstituicao(),
                origem == null ? null : origem.getCargaHoraria(), origem == null ? null : origem.getEmenta(),
                anexo.getTextoExtraido(), pdf);
    }
}
