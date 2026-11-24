package ifsuldeminas.edu.br.ifsystem.aproveitamento.config;

import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.ParecerIa;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.RecomendacaoIa;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.Solicitacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.StatusParecerIa;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.StatusSolicitacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.CoordenadorRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.CursoRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.DisciplinaCursoRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.ParecerIaRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.SolicitacaoRepository;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static ifsuldeminas.edu.br.ifsystem.aproveitamento.config.DemoSeed.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        // Banco proprio em memoria: nao toca ./data/ nem o banco compartilhado dos outros testes.
        "spring.datasource.url=jdbc:h2:mem:demoseed;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@ActiveProfiles("demo")
class DemoSeedTest {

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired
    private DemoSeed seed;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private CursoRepository cursos;
    @Autowired
    private DisciplinaCursoRepository catalogo;
    @Autowired
    private SolicitacaoRepository solicitacoes;
    @Autowired
    private CoordenadorRepository coordenadores;
    @Autowired
    private ParecerIaRepository pareceres;

    @Test
    void criaUsuariosCoordenadorESolicitacoesNosStatusEsperados() {
        assertThat(usuarios.findById(ALUNO_ID)).get().extracting(u -> u.getPerfil()).isEqualTo(Perfil.ALUNO);
        assertThat(usuarios.findById(SERVIDOR_ID)).get().extracting(u -> u.getPerfil()).isEqualTo(Perfil.SERVIDOR);
        assertThat(usuarios.findById(PROFESSOR_ID)).get().extracting(u -> u.getPerfil()).isEqualTo(Perfil.PROFESSOR);
        assertThat(coordenadores.existsByProfessorIdAndCursoNome(PROFESSOR_ID, CURSO)).isTrue();

        Map<java.util.UUID, StatusSolicitacao> status = solicitacoes.findAll().stream()
                .filter(s -> s.getAlunoId().equals(ALUNO_ID))
                .collect(Collectors.toMap(Solicitacao::getId, Solicitacao::getStatus));
        assertThat(status).containsExactlyInAnyOrderEntriesOf(Map.of(
                SOLICITACAO_RASCUNHO_ID, StatusSolicitacao.RASCUNHO,
                SOLICITACAO_ENVIADA_ID, StatusSolicitacao.ENVIADA,
                SOLICITACAO_DEFERIDA_ID, StatusSolicitacao.DEFERIDA));
    }

    @Test
    void geraParecerDaIaParaASolicitacaoEnviada() throws InterruptedException {
        // O parecer e gerado de forma assincrona apos o commit do seed.
        long limite = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < limite
                && pareceres.findFirstBySolicitacaoIdOrderByGeradoEmDesc(SOLICITACAO_ENVIADA_ID)
                .filter(p -> p.getStatus() == StatusParecerIa.CONCLUIDO).isEmpty())
            Thread.sleep(100);

        assertThat(pareceres.findFirstBySolicitacaoIdOrderByGeradoEmDesc(SOLICITACAO_ENVIADA_ID))
                .get().extracting(p -> p.getStatus()).isEqualTo(StatusParecerIa.CONCLUIDO);
    }

    @Test
    void parecersDemoTemRecomendacoesDiferentes() throws InterruptedException {
        long limite = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < limite
                && (concluido(SOLICITACAO_ENVIADA_ID) == null || concluido(SOLICITACAO_DEFERIDA_ID) == null))
            Thread.sleep(100);

        ParecerIa enviada = concluido(SOLICITACAO_ENVIADA_ID);
        ParecerIa deferida = concluido(SOLICITACAO_DEFERIDA_ID);
        assertThat(enviada).isNotNull();
        assertThat(deferida).isNotNull();
        assertThat(deferida.getRecomendacao()).isEqualTo(RecomendacaoIa.SUGERE_DEFERIR);
        assertThat(deferida.getSimilaridade()).isGreaterThanOrEqualTo(70);
        assertThat(enviada.getRecomendacao()).isEqualTo(RecomendacaoIa.SUGERE_INDEFERIR);
        assertThat(enviada.getSimilaridade()).isLessThan(40);
        assertThat(enviada.getModelo()).isEqualTo("heuristica-local-v1");
    }

    @Test
    void catalogoDemoTemUmCursoQuatroDisciplinasComEmentaECoordenadorPorFk() {
        assertThat(cursos.findAll()).singleElement().satisfies(c -> {
            assertThat(c.getCodigo()).isEqualTo(CURSO_CODIGO);
            assertThat(catalogo.findByCursoIdOrderByNome(c.getId())).hasSize(4)
                    .allSatisfy(d -> assertThat(d.getEmenta()).isNotBlank());
            assertThat(coordenadores.existsByProfessorIdAndCursoId(PROFESSOR_ID, c.getId())).isTrue();
        });
        assertThat(solicitacoes.findAll()).allSatisfy(s ->
                assertThat(s.getDisciplinaCursoDestino()).isNotNull());
    }

    private ParecerIa concluido(UUID solicitacaoId) {
        return pareceres.findFirstBySolicitacaoIdOrderByGeradoEmDesc(solicitacaoId)
                .filter(p -> p.getStatus() == StatusParecerIa.CONCLUIDO).orElse(null);
    }

    @Test
    void rodarDeNovoNaoDuplicaDados() {
        long usuariosAntes = usuarios.count();
        long solicitacoesAntes = solicitacoes.count();
        long coordenadoresAntes = coordenadores.count();

        seed.run();

        assertThat(usuarios.count()).isEqualTo(usuariosAntes);
        assertThat(solicitacoes.count()).isEqualTo(solicitacoesAntes);
        assertThat(coordenadores.count()).isEqualTo(coordenadoresAntes);
    }

    @Test
    void rodarDeNovoCorrigeNomesSemAcento() {
        jdbc.update("update cursos set nome = 'Tecnologia em Analise e Desenvolvimento de Sistemas'");
        jdbc.update("update disciplinas_curso set nome = 'Algoritmos e Logica de Programacao' where codigo = 'TADS101'");
        jdbc.update("update solicitacoes_aproveitamento set curso = 'x', disciplina_destino = 'y'");

        seed.run();

        assertThat(jdbc.queryForObject("select nome from cursos where codigo = 'TADS'", String.class))
                .isEqualTo("Tecnologia em Análise e Desenvolvimento de Sistemas");
        assertThat(jdbc.queryForObject("select nome from disciplinas_curso where codigo = 'TADS101'", String.class))
                .isEqualTo("Algoritmos e Lógica de Programação");
        assertThat(jdbc.queryForList("select curso from solicitacoes_aproveitamento", String.class))
                .containsOnly("Tecnologia em Análise e Desenvolvimento de Sistemas");
    }
}
