package ifsuldeminas.edu.br.ifsystem.usuario.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Copia local, somente para leitura nas telas, de um usuario do ifsistem-auth (a fonte da verdade).
 * O id e o sub do token. Colunas legadas (email, matricula, senha...) ficam fora do mapeamento e nulas.
 */
@Entity
@Table(name = "usuarios", uniqueConstraints = @UniqueConstraint(name = "uk_usuario_cpf", columnNames = "cpf"))
public class Usuario {
    @Id
    private UUID id;
    @Column(nullable = false)
    private String nome;
    @Column(nullable = false, length = 11)
    private String cpf;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Perfil perfil;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusUsuario status;
    @Column(nullable = false, updatable = false)
    private OffsetDateTime criadoEm;
    @Column(nullable = false)
    private OffsetDateTime atualizadoEm;

    protected Usuario() {
    }

    public Usuario(UUID id) {
        this.id = id;
        this.criadoEm = OffsetDateTime.now();
        this.atualizadoEm = this.criadoEm;
    }

    /** Aplica os dados vindos do auth (claims do token ou GET /usuarios). */
    public void sincronizar(String nome, String cpf, Perfil perfil, boolean ativo) {
        this.nome = nome == null || nome.isBlank() ? "(sem nome)" : nome;
        this.cpf = Cpf.normalizar(cpf);
        this.perfil = perfil;
        this.status = ativo ? StatusUsuario.ATIVO : StatusUsuario.INATIVO;
    }

    @PreUpdate
    void atualizarData() {
        atualizadoEm = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getCpf() {
        return cpf;
    }

    public Perfil getPerfil() {
        return perfil;
    }

    public StatusUsuario getStatus() {
        return status;
    }

    /** Ativo = status ATIVO. */
    public boolean isAtivo() {
        return status == StatusUsuario.ATIVO;
    }
}
