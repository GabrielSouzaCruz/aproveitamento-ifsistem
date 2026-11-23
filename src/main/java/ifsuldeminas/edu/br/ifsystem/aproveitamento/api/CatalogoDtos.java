package ifsuldeminas.edu.br.ifsystem.aproveitamento.api;

import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.Coordenador;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.Curso;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.DisciplinaCurso;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class CatalogoDtos {
    private CatalogoDtos() {
    }

    public record CursoRequest(@NotBlank @Size(max = 50) String codigo, @NotBlank @Size(max = 255) String nome) {
    }

    public record DisciplinaCursoRequest(
            @NotBlank @Size(max = 50) String codigo,
            @NotBlank @Size(max = 255) String nome,
            @NotNull @Positive Integer cargaHoraria,
            @NotBlank String ementa) {
    }

    public record CoordenadorRequest(@NotNull UUID professorId) {
    }

    public record CursoResposta(UUID id, String codigo, String nome, boolean ativo) {
        public static CursoResposta de(Curso c) {
            return new CursoResposta(c.getId(), c.getCodigo(), c.getNome(), c.isAtivo());
        }
    }

    public record DisciplinaCursoResposta(UUID id, UUID cursoId, String codigo, String nome, Integer cargaHoraria,
                                          String ementa, boolean ativo) {
        public static DisciplinaCursoResposta de(DisciplinaCurso d) {
            return new DisciplinaCursoResposta(d.getId(), d.getCurso().getId(), d.getCodigo(), d.getNome(),
                    d.getCargaHoraria(), d.getEmenta(), d.isAtivo());
        }
    }

    public record CoordenadorResposta(UUID id, UUID professorId, UUID cursoId) {
        public static CoordenadorResposta de(Coordenador c) {
            return new CoordenadorResposta(c.getId(), c.getProfessorId(), c.getCurso().getId());
        }
    }
}
