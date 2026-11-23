package ifsuldeminas.edu.br.ifsystem.aproveitamento.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "anexos_aproveitamento")
public class Anexo {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitacao_id", nullable = false)
    private Solicitacao solicitacao;
    @Column(nullable = false)
    private String nomeArquivo;
    @Column(nullable = false)
    private String tipoConteudo;
    @Column(nullable = false)
    private Long tamanho;
    @Column(nullable = false, updatable = false)
    private OffsetDateTime criadoEm;
    @Column(columnDefinition = "text")
    private String textoExtraido;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusVerificacao statusVerificacao = StatusVerificacao.VERIFICANDO;
    @Column(columnDefinition = "text")
    private String motivoVerificacao;
    @Enumerated(EnumType.STRING)
    private TipoDetectado tipoDetectado;
    private OffsetDateTime verificadoEm;
    private String modeloVerificacao;

    protected Anexo() {
    }

    public Anexo(Solicitacao solicitacao, String nomeArquivo, String tipoConteudo, Long tamanho) {
        this.solicitacao = solicitacao;
        this.nomeArquivo = nomeArquivo;
        this.tipoConteudo = tipoConteudo;
        this.tamanho = tamanho;
        this.criadoEm = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public Solicitacao getSolicitacao() {
        return solicitacao;
    }

    public String getNomeArquivo() {
        return nomeArquivo;
    }

    public String getTipoConteudo() {
        return tipoConteudo;
    }

    public Long getTamanho() {
        return tamanho;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public String getTextoExtraido() {
        return textoExtraido;
    }

    public void setTextoExtraido(String textoExtraido) {
        this.textoExtraido = textoExtraido;
    }

    public StatusVerificacao getStatusVerificacao() {
        return statusVerificacao;
    }

    public String getMotivoVerificacao() {
        return motivoVerificacao;
    }

    public TipoDetectado getTipoDetectado() {
        return tipoDetectado;
    }

    public OffsetDateTime getVerificadoEm() {
        return verificadoEm;
    }

    public String getModeloVerificacao() {
        return modeloVerificacao;
    }

    /** Volta o anexo para a fila de verificacao (envio inicial ou reverificacao). */
    public void marcarVerificando() {
        this.statusVerificacao = StatusVerificacao.VERIFICANDO;
        this.motivoVerificacao = null;
        this.tipoDetectado = null;
        this.verificadoEm = null;
        this.modeloVerificacao = null;
    }

    public void registrarVerificacao(StatusVerificacao status, TipoDetectado tipo, String motivo, String modelo) {
        this.statusVerificacao = status;
        this.tipoDetectado = tipo;
        this.motivoVerificacao = motivo;
        this.modeloVerificacao = modelo;
        this.verificadoEm = OffsetDateTime.now();
    }
}
