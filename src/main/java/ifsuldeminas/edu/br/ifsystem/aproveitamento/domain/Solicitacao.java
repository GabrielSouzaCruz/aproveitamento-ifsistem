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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "solicitacoes_aproveitamento")
public class Solicitacao {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private UUID alunoId;
    @Column(nullable = false)
    private String curso;
    @Column(nullable = false)
    private String disciplinaDestino;
    @Column(nullable = false)
    private Integer cargaHorariaDestino;
    /** Disciplina do catalogo; nula nas solicitacoes anteriores ao catalogo (os textos acima ficam como historico). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disciplina_destino_id")
    private DisciplinaCurso disciplinaCursoDestino;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusSolicitacao status;
    @Column(nullable = false, updatable = false)
    private OffsetDateTime criadoEm;
    @Column(nullable = false)
    private OffsetDateTime atualizadoEm;
    @Version
    private Long version;

    protected Solicitacao() {
    }

    public Solicitacao(UUID alunoId, String curso, String disciplinaDestino, Integer cargaHorariaDestino) {
        this.alunoId = alunoId;
        this.curso = curso;
        this.disciplinaDestino = disciplinaDestino;
        this.cargaHorariaDestino = cargaHorariaDestino;
        this.status = StatusSolicitacao.RASCUNHO;
        this.criadoEm = OffsetDateTime.now();
        this.atualizadoEm = this.criadoEm;
    }

    /** Copia nome, curso e carga horaria da disciplina do catalogo para os campos de texto. */
    public Solicitacao(UUID alunoId, DisciplinaCurso destino) {
        this(alunoId, destino.getCurso().getNome(), destino.getNome(), destino.getCargaHoraria());
        this.disciplinaCursoDestino = destino;
    }

    @PreUpdate
    void atualizarData() {
        atualizadoEm = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getAlunoId() {
        return alunoId;
    }

    public String getCurso() {
        return curso;
    }

    public String getDisciplinaDestino() {
        return disciplinaDestino;
    }

    public Integer getCargaHorariaDestino() {
        return cargaHorariaDestino;
    }

    public DisciplinaCurso getDisciplinaCursoDestino() {
        return disciplinaCursoDestino;
    }

    public StatusSolicitacao getStatus() {
        return status;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    /** Troca o destino, recopiando nome, curso e carga horaria do catalogo. */
    public void alterarDestino(DisciplinaCurso destino) {
        this.curso = destino.getCurso().getNome();
        this.disciplinaDestino = destino.getNome();
        this.cargaHorariaDestino = destino.getCargaHoraria();
        this.disciplinaCursoDestino = destino;
    }

    public void alterarStatus(StatusSolicitacao status) {
        this.status = status;
    }
}
