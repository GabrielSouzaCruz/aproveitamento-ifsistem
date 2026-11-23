package ifsuldeminas.edu.br.ifsystem.aproveitamento.repository;

import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.DisciplinaCurso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisciplinaCursoRepository extends JpaRepository<DisciplinaCurso, UUID> {
    boolean existsByCursoIdAndCodigo(UUID cursoId, String codigo);

    boolean existsByCursoIdAndCodigoAndIdNot(UUID cursoId, String codigo, UUID id);

    List<DisciplinaCurso> findByCursoIdOrderByNome(UUID cursoId);

    List<DisciplinaCurso> findByCursoIdAndAtivoTrueOrderByNome(UUID cursoId);

    Optional<DisciplinaCurso> findByIdAndCursoId(UUID id, UUID cursoId);
}
