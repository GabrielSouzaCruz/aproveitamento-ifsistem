package ifsuldeminas.edu.br.ifsystem.aproveitamento.repository;

import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.Solicitacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.StatusSolicitacao;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SolicitacaoRepository extends JpaRepository<Solicitacao, UUID> {
    /** Carrega a disciplina do catalogo junto: o parecer da IA roda fora de transacao (sem open-in-view). */
    @Override
    @EntityGraph(attributePaths = "disciplinaCursoDestino")
    Optional<Solicitacao> findById(UUID id);

    List<Solicitacao> findByAlunoId(UUID alunoId);

    Optional<Solicitacao> findBySolicitacaoOrigemId(UUID origemId);

    List<Solicitacao> findByStatus(StatusSolicitacao status);
}
