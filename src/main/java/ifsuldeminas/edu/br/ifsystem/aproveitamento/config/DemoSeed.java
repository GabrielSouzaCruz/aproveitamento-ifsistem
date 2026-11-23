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
import ifsuldeminas.edu.br.ifsystem.usuario.domain.StatusUsuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Dados de exemplo para demonstracao. So roda com o profile "demo" ou com {@code DEMO_SEED=true}
 * (propriedade {@code app.demo-seed}); em producao (profile "render") o padrao e desligado.
 * <p>
 * Usuarios e solicitacoes sao inseridos via JDBC para terem UUIDs fixos (as entidades usam
 * {@code @GeneratedValue}); os avancos de status passam pelo {@link FluxoStatusService}, gerando
 * historico e o parecer da IA (assincrono, apos o commit) como no uso real.
 * Idempotente: se o aluno demo ja existe, nada e feito.
 */
@Component
@Conditional(DemoSeed.Ativado.class)
public class DemoSeed implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoSeed.class);

    public static final String CURSO_CODIGO = "TADS";
    public static final String CURSO = "Tecnologia em Analise e Desenvolvimento de Sistemas";
    public static final String SENHA = "demo1234";
    public static final String CPF_ALUNO = "52998224725";
    public static final String CPF_SERVIDOR = "11144477735";
    public static final String CPF_PROFESSOR = "12345678909";
    public static final UUID ALUNO_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID SERVIDOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID PROFESSOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    public static final UUID SOLICITACAO_RASCUNHO_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    public static final UUID SOLICITACAO_ENVIADA_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
    public static final UUID SOLICITACAO_DEFERIDA_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000003");

    static final String EMENTA_BANCO_DADOS = "Modelagem de dados: modelo entidade-relacionamento e modelo relacional. "
            + "Normalizacao de tabelas. Linguagem SQL: criacao de tabelas, consultas, juncoes, funcoes de agregacao "
            + "e subconsultas. Transacoes e controle de concorrencia. Projeto de banco de dados relacional.";
    static final String EMENTA_PROGRAMACAO = "Introducao a algoritmos e logica de programacao. Variaveis, tipos de "
            + "dados e operadores. Estruturas condicionais e estruturas de repeticao. Vetores e matrizes. "
            + "Funcoes e modularizacao. Resolucao de problemas com programacao estruturada.";
    static final String EMENTA_ENGENHARIA = "Processo de desenvolvimento de software. Levantamento e analise de "
            + "requisitos. Modelagem com UML. Metodologias ageis, Scrum e Kanban. Testes de software e controle de "
            + "versao. Gerenciamento de projetos de software.";
    static final String EMENTA_REDES = "Fundamentos de redes de computadores. Modelo OSI e arquitetura TCP/IP. "
            + "Enderecamento IP, roteamento e protocolos de transporte. Servicos de rede, DNS e HTTP. "
            + "Seguranca basica em redes.";

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
    private final PasswordEncoder encoder;
    private final FluxoStatusService fluxo;
    private final SolicitacaoRepository solicitacoes;
    private final DisciplinaCursadaRepository disciplinas;
    private final CoordenadorRepository coordenadores;
    private final CursoRepository cursos;
    private final DisciplinaCursoRepository catalogo;

    public DemoSeed(JdbcTemplate jdbc, TransactionTemplate tx, PasswordEncoder encoder, FluxoStatusService fluxo,
                    SolicitacaoRepository solicitacoes, DisciplinaCursadaRepository disciplinas,
                    CoordenadorRepository coordenadores, CursoRepository cursos, DisciplinaCursoRepository catalogo) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.encoder = encoder;
        this.fluxo = fluxo;
        this.solicitacoes = solicitacoes;
        this.disciplinas = disciplinas;
        this.coordenadores = coordenadores;
        this.cursos = cursos;
        this.catalogo = catalogo;
    }

    @Override
    public void run(String... args) {
        Integer existentes = jdbc.queryForObject("select count(*) from usuarios where id = ?", Integer.class, ALUNO_ID);
        if (existentes != null && existentes > 0) {
            // Bancos semeados antes do login real: atualiza CPF (valido) e senha dos usuarios demo.
            String senha = encoder.encode(SENHA);
            jdbc.update("update usuarios set cpf = ?, senha = ? where id = ?", CPF_ALUNO, senha, ALUNO_ID);
            jdbc.update("update usuarios set cpf = ?, senha = ? where id = ?", CPF_SERVIDOR, senha, SERVIDOR_ID);
            jdbc.update("update usuarios set cpf = ?, senha = ? where id = ?", CPF_PROFESSOR, senha, PROFESSOR_ID);
            log.info("Seed demo ja existe: credenciais demo atualizadas");
            return;
        }
        // Uma transacao so: ou entra tudo, ou nada. Os eventos de envio disparam o parecer apos o commit.
        tx.executeWithoutResult(status -> popular());
        log.info("Seed demo criado: aluno {}, servidor {}, professor {}", ALUNO_ID, SERVIDOR_ID, PROFESSOR_ID);
    }

    private void popular() {
        String senha = encoder.encode(SENHA);
        inserirUsuario(ALUNO_ID, "Ana Aluna Demo", CPF_ALUNO, "aluno.demo@ifsuldeminas.edu.br",
                "DEMO-ALU-001", senha, CURSO_CODIGO, 3, Perfil.ALUNO);
        inserirUsuario(SERVIDOR_ID, "Sergio Servidor Demo", CPF_SERVIDOR, "servidor.demo@ifsuldeminas.edu.br",
                "DEMO-SRV-001", senha, null, null, Perfil.SERVIDOR);
        inserirUsuario(PROFESSOR_ID, "Paula Professora Demo", CPF_PROFESSOR, "professor.demo@ifsuldeminas.edu.br",
                "DEMO-PRF-001", senha, null, null, Perfil.PROFESSOR);

        Curso tads = cursos.saveAndFlush(new Curso(CURSO_CODIGO, CURSO));
        coordenadores.save(new Coordenador(PROFESSOR_ID, tads));
        DisciplinaCurso programacao = catalogo.saveAndFlush(new DisciplinaCurso(tads, "TADS101",
                "Algoritmos e Logica de Programacao", 80, EMENTA_PROGRAMACAO));
        DisciplinaCurso bancoDados = catalogo.saveAndFlush(new DisciplinaCurso(tads, "TADS201",
                "Banco de Dados", 60, EMENTA_BANCO_DADOS));
        DisciplinaCurso engenharia = catalogo.saveAndFlush(new DisciplinaCurso(tads, "TADS301",
                "Engenharia de Software", 80, EMENTA_ENGENHARIA));
        catalogo.saveAndFlush(new DisciplinaCurso(tads, "TADS202", "Redes de Computadores", 60, EMENTA_REDES));

        // Rascunho: ementa de origem sobre outro assunto (nao enviado, sem parecer).
        inserirSolicitacao(SOLICITACAO_RASCUNHO_ID, engenharia,
                "Engenharia de Software I", "Universidade Federal de Lavras", 72,
                "Historia da engenharia civil. Resistencia dos materiais e estruturas de concreto. "
                        + "Canteiro de obras, orcamento e fiscalizacao de construcoes.");
        // Ementa diferente da do catalogo: o parecer sugere indeferir.
        inserirSolicitacao(SOLICITACAO_ENVIADA_ID, bancoDados,
                "Banco de Dados I", "Universidade Federal de Alfenas", 64,
                "Historia da computacao e geracoes de computadores. Hardware, perifericos e sistemas "
                        + "operacionais. Uso de editores de texto, planilhas eletronicas e apresentacoes. "
                        + "Navegacao na internet e correio eletronico.");
        // Ementa parecida com a do catalogo: o parecer sugere deferir.
        inserirSolicitacao(SOLICITACAO_DEFERIDA_ID, programacao,
                "Introducao a Programacao", "Universidade Federal de Itajuba", 80,
                "Introducao a algoritmos e logica de programacao. Variaveis, tipos de dados e operadores. "
                        + "Estruturas condicionais e estruturas de repeticao. Vetores e matrizes. "
                        + "Funcoes e modularizacao. Resolucao de problemas com programacao estruturada.");

        fluxo.executar(SOLICITACAO_ENVIADA_ID, "enviar", ALUNO_ID, Perfil.ALUNO, "Envio de demonstracao");

        fluxo.executar(SOLICITACAO_DEFERIDA_ID, "enviar", ALUNO_ID, Perfil.ALUNO, "Envio de demonstracao");
        fluxo.executar(SOLICITACAO_DEFERIDA_ID, "conferir", SERVIDOR_ID, Perfil.SERVIDOR, "Documentacao conferida");
        fluxo.executar(SOLICITACAO_DEFERIDA_ID, "concluir-pre-diagnostico", SERVIDOR_ID, Perfil.SERVIDOR,
                "Pre-diagnostico concluido");
        fluxo.executar(SOLICITACAO_DEFERIDA_ID, "analisar", PROFESSOR_ID, Perfil.PROFESSOR, "Ementas equivalentes");
        fluxo.executar(SOLICITACAO_DEFERIDA_ID, "deferir", PROFESSOR_ID, Perfil.PROFESSOR, "Aproveitamento deferido");
    }

    private void inserirUsuario(UUID id, String nome, String cpf, String email, String matricula, String senha,
                                String curso, Integer periodo, Perfil perfil) {
        OffsetDateTime agora = OffsetDateTime.now();
        jdbc.update("""
                        insert into usuarios (id, nome, cpf, email, matricula, senha, telefone, curso, periodo,
                                              perfil, status, criado_em, atualizado_em)
                        values (?, ?, ?, ?, ?, ?, null, ?, ?, ?, ?, ?, ?)""",
                id, nome, cpf, email, matricula, senha, curso, periodo,
                perfil.name(), StatusUsuario.ATIVO.name(), agora, agora);
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
