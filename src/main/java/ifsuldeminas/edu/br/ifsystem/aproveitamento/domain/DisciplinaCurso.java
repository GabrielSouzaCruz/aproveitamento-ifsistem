package ifsuldeminas.edu.br.ifsystem.aproveitamento.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/** Disciplina do catalogo de um curso, mantida pela secretaria; a ementa alimenta o parecer da IA. */
@Entity
@Table(name = "disciplinas_curso", uniqueConstraints = {
        @UniqueConstraint(name = "uk_disciplina_curso_codigo", columnNames = {"curso_id", "codigo"})
})
public class DisciplinaCurso {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curso_id", nullable = false)
    private Curso curso;
    @Column(nullable = false)
    private String codigo;
    @Column(nullable = false)
    private String nome;
    @Column(nullable = false)
    private Integer cargaHoraria;
    @Column(nullable = false, columnDefinition = "text")
    private String ementa;
    @Column(nullable = false)
    private boolean ativo = true;

    protected DisciplinaCurso() {
    }

    public DisciplinaCurso(Curso curso, String codigo, String nome, Integer cargaHoraria, String ementa) {
        this.curso = curso;
        this.codigo = codigo;
        this.nome = nome;
        this.cargaHoraria = cargaHoraria;
        this.ementa = ementa;
    }

    public UUID getId() {
        return id;
    }

    public Curso getCurso() {
        return curso;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNome() {
        return nome;
    }

    public Integer getCargaHoraria() {
        return cargaHoraria;
    }

    public String getEmenta() {
        return ementa;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void atualizar(String codigo, String nome, Integer cargaHoraria, String ementa) {
        this.codigo = codigo;
        this.nome = nome;
        this.cargaHoraria = cargaHoraria;
        this.ementa = ementa;
    }

    public void definirAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}
