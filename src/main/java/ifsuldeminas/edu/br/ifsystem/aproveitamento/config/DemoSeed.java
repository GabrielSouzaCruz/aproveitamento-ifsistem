package ifsuldeminas.edu.br.ifsystem.aproveitamento.config;

import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.Coordenador;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.DisciplinaCursada;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.StatusSolicitacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.CoordenadorRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.DisciplinaCursadaRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.repository.SolicitacaoRepository;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.service.FluxoStatusService;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.StatusUsuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Dados de exemplo para demonstracao (profile "demo").
 * <p>
 * Usuarios e solicitacoes sao inseridos via JDBC para terem UUIDs fixos (as entidades usam
 * {@code @GeneratedValue}); os avancos de status passam pelo {@link FluxoStatusService}, gerando
 * historico e o parecer da IA (assincrono, apos o commit) como no uso real.
 * Idempotente: se o aluno demo ja existe, nada e feito.
 */
@Component
@Profile("demo")
public class DemoSeed implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoSeed.class);

    public static final String CURSO = "ADS";
    public static final String SENHA = "demo123";
    public static final UUID ALUNO_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID SERVIDOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID PROFESSOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    public static final UUID SOLICITACAO_RASCUNHO_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    public static final UUID SOLICITACAO_ENVIADA_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
    public static final UUID SOLICITACAO_DEFERIDA_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000003");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final PasswordEncoder encoder;
    private final FluxoStatusService fluxo;
    private final SolicitacaoRepository solicitacoes;
    private final DisciplinaCursadaRepository disciplinas;
    private final CoordenadorRepository coordenadores;

    public DemoSeed(JdbcTemplate jdbc, TransactionTemplate tx, PasswordEncoder encoder, FluxoStatusService fluxo,
                    SolicitacaoRepository solicitacoes, DisciplinaCursadaRepository disciplinas,
                    CoordenadorRepository coordenadores) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.encoder = encoder;
        this.fluxo = fluxo;
        this.solicitacoes = solicitacoes;
        this.disciplinas = disciplinas;
        this.coordenadores = coordenadores;
    }

    @Override
    public void run(String... args) {
        Integer existentes = jdbc.queryForObject("select count(*) from usuarios where id = ?", Integer.class, ALUNO_ID);
        if (existentes != null && existentes > 0) {
            log.info("Seed demo ignorado: dados de demonstracao ja existem");
            return;
        }
        // Uma transacao so: ou entra tudo, ou nada. Os eventos de envio disparam o parecer apos o commit.
        tx.executeWithoutResult(status -> popular());
        log.info("Seed demo criado: aluno {}, servidor {}, professor {}", ALUNO_ID, SERVIDOR_ID, PROFESSOR_ID);
    }

    private void popular() {
        String senha = encoder.encode(SENHA);
        inserirUsuario(ALUNO_ID, "Ana Aluna Demo", "11111111111", "aluno.demo@ifsuldeminas.edu.br",
                "DEMO-ALU-001", senha, CURSO, 3, Perfil.ALUNO);
        inserirUsuario(SERVIDOR_ID, "Sergio Servidor Demo", "22222222222", "servidor.demo@ifsuldeminas.edu.br",
                "DEMO-SRV-001", senha, null, null, Perfil.SERVIDOR);
        inserirUsuario(PROFESSOR_ID, "Paula Professora Demo", "33333333333", "professor.demo@ifsuldeminas.edu.br",
                "DEMO-PRF-001", senha, null, null, Perfil.PROFESSOR);
        coordenadores.save(new Coordenador(PROFESSOR_ID, CURSO));

        inserirSolicitacao(SOLICITACAO_RASCUNHO_ID, "Engenharia de Software", 80,
                "Engenharia de Software I", "Universidade Federal de Lavras", 72);
        inserirSolicitacao(SOLICITACAO_ENVIADA_ID, "Banco de Dados", 60,
                "Banco de Dados I", "Universidade Federal de Alfenas", 64);
        inserirSolicitacao(SOLICITACAO_DEFERIDA_ID, "Algoritmos e Logica de Programacao", 80,
                "Introducao a Programacao", "Universidade Federal de Itajuba", 80);

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

    private void inserirSolicitacao(UUID id, String disciplinaDestino, int cargaDestino,
                                    String disciplinaOrigem, String instituicao, int cargaOrigem) {
        OffsetDateTime agora = OffsetDateTime.now();
        jdbc.update("""
                        insert into solicitacoes_aproveitamento (id, aluno_id, curso, disciplina_destino,
                                                                 carga_horaria_destino, status, criado_em,
                                                                 atualizado_em, version)
                        values (?, ?, ?, ?, ?, ?, ?, ?, 0)""",
                id, ALUNO_ID, CURSO, disciplinaDestino, cargaDestino,
                StatusSolicitacao.RASCUNHO.name(), agora, agora);
        disciplinas.save(new DisciplinaCursada(solicitacoes.getReferenceById(id),
                disciplinaOrigem, instituicao, cargaOrigem));
    }
}
