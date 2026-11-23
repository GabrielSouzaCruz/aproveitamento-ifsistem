package ifsuldeminas.edu.br.ifsystem.aproveitamento.repository;

import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.Coordenador;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoordenadorRepository extends JpaRepository<Coordenador, UUID> {
    boolean existsByProfessorIdAndCursoId(UUID professorId, UUID cursoId);

    /** Fallback para solicitacoes anteriores ao catalogo, que so guardam o nome do curso. */
    boolean existsByProfessorIdAndCursoNome(UUID professorId, String cursoNome);

    Optional<Coordenador> findByProfessorIdAndCursoId(UUID professorId, UUID cursoId);

    List<Coordenador> findByCursoId(UUID cursoId);
}
