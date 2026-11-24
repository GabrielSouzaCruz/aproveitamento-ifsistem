package ifsuldeminas.edu.br.ifsystem.aproveitamento.config;

import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.Coordenador;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.Curso;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.DisciplinaCurso;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.DisciplinaCursada;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.StatusSolicitacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.CoordenadorRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.CursoRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.DisciplinaCursadaRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.DisciplinaCursoRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.SolicitacaoRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.service.FluxoStatusService;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Perfil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Dados de exemplo para demonstracao. So roda com o profile "demo" ou com {@code DEMO_SEED=true}
 * (propriedade {@code app.demo-seed}); em producao (profile "render") o padrao e desligado.
 * <p>
 * Nao cria usuarios: eles vivem no ifsistem-auth e os UUIDs fixos abaixo sao os de la (a copia local entra no
 * primeiro login). Solicitacoes sao inseridas via JDBC para terem UUIDs fixos; os avancos de status passam pelo {@link FluxoStatusService}, gerando
 * historico e o parecer da IA (assincrono, apos o commit) como no uso real.
 * Idempotente: se o curso demo ja existe, so os textos sao atualizados.
 */
@Component
@Conditional(DemoSeed.Ativado.class)
public class DemoSeed implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoSeed.class);

    public static final String CURSO_CODIGO = "TADS";
    public static final String CURSO = "Tecnologia em Análise e Desenvolvimento de Sistemas";
    public static final UUID ALUNO_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID SERVIDOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID PROFESSOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    public static final UUID SOLICITACAO_RASCUNHO_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    public static final UUID SOLICITACAO_ENVIADA_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
    public static final UUID SOLICITACAO_DEFERIDA_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000003");

    static final String EMENTA_BANCO_DADOS = "Modelagem de dados: modelo entidade-relacionamento e modelo relacional. "
            + "Normalização de tabelas. Linguagem SQL: criação de tabelas, consultas, junções, funções de agregação "
            + "e subconsultas. Transações e controle de concorrência. Projeto de banco de dados relacional.";
    static final String EMENTA_PROGRAMACAO = "Introdução a algoritmos e lógica de programação. Variáveis, tipos de "
            + "dados e operadores. Estruturas condicionais e estruturas de repetição. Vetores e matrizes. "
            + "Funções e modularização. Resolução de problemas com programação estruturada.";
    static final String EMENTA_ENGENHARIA = "Processo de desenvolvimento de software. Levantamento e análise de "
            + "requisitos. Modelagem com UML. Metodologias ágeis, Scrum e Kanban. Testes de software e controle de "
            + "versao. Gerenciamento de projetos de software.";
    static final String EMENTA_REDES = "Fundamentos de redes de computadores. Modelo OSI e arquitetura TCP/IP. "
            + "Endereçamento IP, roteamento e protocolos de transporte. Serviços de rede, DNS e HTTP. "
            + "Segurança básica em redes.";

    static final String EMENTA_ORIGEM_RASCUNHO = "História da engenharia civil. Resistência dos materiais e estruturas "
            + "de concreto. Canteiro de obras, orçamento e fiscalização de construções.";
    static final String EMENTA_ORIGEM_ENVIADA = "História da computação e gerações de computadores. Hardware, "
            + "periféricos e sistemas operacionais. Uso de editores de texto, planilhas eletrônicas e apresentações. "
            + "Navegação na internet e correio eletrônico.";
    static final String EMENTA_ORIGEM_DEFERIDA = "Introdução a algoritmos e lógica de programação. Variáveis, tipos de "
            + "dados e operadores. Estruturas condicionais e estruturas de repetição. Vetores e matrizes. "
            + "Funções e modularização. Resolução de problemas com programação estruturada.";

    /** Liga o seed: profile "demo" ou app.demo-seed=true (variavel DEMO_SEED). */
    static class Ativado implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            var env = context.getEnvironment();
            return env.acceptsProfiles(Profiles.of("demo"))
                    || env.getProperty("app.demo-seed", Boolean.class, false);
        }
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final FluxoStatusService fluxo;
    private final SolicitacaoRepository solicitacoes;
    private final DisciplinaCursadaRepository disciplinas;
    private final CoordenadorRepository coordenadores;
    private final CursoRepository cursos;
    private final DisciplinaCursoRepository catalogo;

    public DemoSeed(JdbcTemplate jdbc, TransactionTemplate tx, FluxoStatusService fluxo,
                    SolicitacaoRepository solicitacoes, DisciplinaCursadaRepository disciplinas,
                    CoordenadorRepository coordenadores, CursoRepository cursos, DisciplinaCursoRepository catalogo) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.fluxo = fluxo;
        this.solicitacoes = solicitacoes;
        this.disciplinas = disciplinas;
        this.coordenadores = coordenadores;
        this.cursos = cursos;
        this.catalogo = catalogo;
    }

    @Override
    public void run(String... args) {
        Integer existentes = jdbc.queryForObject("select count(*) from cursos where codigo = ?", Integer.class, CURSO_CODIGO);
        if (existentes != null && existentes > 0) {
            tx.executeWithoutResult(status -> atualizarTextos());
            log.info("Seed demo ja existe: textos demo atualizados");
            return;
        }
        // Uma transacao so: ou entra tudo, ou nada. Os eventos de envio disparam o parecer apos o commit.
        tx.executeWithoutResult(status -> popular());
        log.info("Seed demo criado (usuarios do auth): aluno {}, servidor {}, professor {}", ALUNO_ID, SERVIDOR_ID, PROFESSOR_ID);
    }

    /** Bancos ja semeados (por ID/codigo): corrige textos para a grafia com acentos. */
    private void atualizarTextos() {
        jdbc.update("update cursos set nome = ? where codigo = ?", CURSO, CURSO_CODIGO);
        String[][] catalogoDemo = {
                {"TADS101", "Algoritmos e Lógica de Programação", EMENTA_PROGRAMACAO},
                {"TADS201", "Banco de Dados", EMENTA_BANCO_DADOS},
                {"TADS301", "Engenharia de Software", EMENTA_ENGENHARIA},
                {"TADS202", "Redes de Computadores", EMENTA_REDES}};
        for (String[] d : catalogoDemo) {
            jdbc.update("update disciplinas_curso set nome = ?, ementa = ? where codigo = ? "
                    + "and curso_id = (select id from cursos where codigo = ?)", d[1], d[2], d[0], CURSO_CODIGO);
        }
        // Copia gravada na solicitacao: acompanha o catalogo.
        jdbc.update("update solicitacoes_aproveitamento set "
                + "disciplina_destino = (select d.nome from disciplinas_curso d where d.id = disciplina_destino_id), "
                + "curso = (select c.nome from disciplinas_curso d join cursos c on c.id = d.curso_id "
                + "where d.id = disciplina_destino_id) where id in (?, ?, ?)",
                SOLICITACAO_RASCUNHO_ID, SOLICITACAO_ENVIADA_ID, SOLICITACAO_DEFERIDA_ID);
        Object[][] origens = {
                {SOLICITACAO_RASCUNHO_ID, "Engenharia de Software I", "Universidade Federal de Lavras", EMENTA_ORIGEM_RASCUNHO},
                {SOLICITACAO_ENVIADA_ID, "Banco de Dados I", "Universidade Federal de Alfenas", EMENTA_ORIGEM_ENVIADA},
                {SOLICITACAO_DEFERIDA_ID, "Introdução à Programação", "Universidade Federal de Itajubá", EMENTA_ORIGEM_DEFERIDA}};
        for (Object[] o : origens) {
            jdbc.update("update disciplinas_cursadas set nome = ?, instituicao = ?, ementa = ? where solicitacao_id = ?",
                    o[1], o[2], o[3], o[0]);
        }
    }

    private void popular() {
        Curso tads = cursos.saveAndFlush(new Curso(CURSO_CODIGO, CURSO));
        coordenadores.save(new Coordenador(PROFESSOR_ID, tads));
        DisciplinaCurso programacao = catalogo.saveAndFlush(new DisciplinaCurso(tads, "TADS101",
                "Algoritmos e Lógica de Programação", 80, EMENTA_PROGRAMACAO));
        DisciplinaCurso bancoDados = catalogo.saveAndFlush(new DisciplinaCurso(tads, "TADS201",
                "Banco de Dados", 60, EMENTA_BANCO_DADOS));
        DisciplinaCurso engenharia = catalogo.saveAndFlush(new DisciplinaCurso(tads, "TADS301",
                "Engenharia de Software", 80, EMENTA_ENGENHARIA));
        catalogo.saveAndFlush(new DisciplinaCurso(tads, "TADS202", "Redes de Computadores", 60, EMENTA_REDES));

        // Rascunho: ementa de origem sobre outro assunto (nao enviado, sem parecer).
        inserirSolicitacao(SOLICITACAO_RASCUNHO_ID, engenharia, "Engenharia de Software I",
                "Universidade Federal de Lavras", 72, EMENTA_ORIGEM_RASCUNHO);
        // Ementa diferente da do catalogo: o parecer sugere indeferir.
        inserirSolicitacao(SOLICITACAO_ENVIADA_ID, bancoDados, "Banco de Dados I",
                "Universidade Federal de Alfenas", 64, EMENTA_ORIGEM_ENVIADA);
        // Ementa parecida com a do catalogo: o parecer sugere deferir.
        inserirSolicitacao(SOLICITACAO_DEFERIDA_ID, programacao, "Introdução à Programação",
                "Universidade Federal de Itajubá", 80, EMENTA_ORIGEM_DEFERIDA);

        fluxo.executar(SOLICITACAO_ENVIADA_ID, "enviar", ALUNO_ID, Perfil.ALUNO, "Envio de demonstração");

        fluxo.executar(SOLICITACAO_DEFERIDA_ID, "enviar", ALUNO_ID, Perfil.ALUNO, "Envio de demonstração");
        fluxo.executar(SOLICITACAO_DEFERIDA_ID, "conferir", SERVIDOR_ID, Perfil.SERVIDOR, "Documentação conferida");
        fluxo.executar(SOLICITACAO_DEFERIDA_ID, "concluir-pre-diagnostico", SERVIDOR_ID, Perfil.SERVIDOR,
                "Pré-diagnóstico concluído");
        fluxo.executar(SOLICITACAO_DEFERIDA_ID, "analisar", PROFESSOR_ID, Perfil.PROFESSOR, "Ementas equivalentes");
        fluxo.executar(SOLICITACAO_DEFERIDA_ID, "deferir", PROFESSOR_ID, Perfil.PROFESSOR, "Aproveitamento deferido");
    }

    private void inserirSolicitacao(UUID id, DisciplinaCurso destino, String disciplinaOrigem, String instituicao,
                                    int cargaOrigem, String ementaOrigem) {
        OffsetDateTime agora = OffsetDateTime.now();
        jdbc.update("""
                        insert into solicitacoes_aproveitamento (id, aluno_id, curso, disciplina_destino,
                                                                 carga_horaria_destino, disciplina_destino_id, status,
                                                                 criado_em, atualizado_em, version)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)""",
                id, ALUNO_ID, destino.getCurso().getNome(), destino.getNome(), destino.getCargaHoraria(),
                destino.getId(), StatusSolicitacao.RASCUNHO.name(), agora, agora);
        disciplinas.save(new DisciplinaCursada(solicitacoes.getReferenceById(id),
                disciplinaOrigem, instituicao, cargaOrigem, ementaOrigem));
    }
}
