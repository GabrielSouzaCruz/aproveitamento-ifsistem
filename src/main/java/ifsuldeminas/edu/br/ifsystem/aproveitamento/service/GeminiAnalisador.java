package ifsuldeminas.edu.br.ifsystem.aproveitamento.service;

import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.RecomendacaoIa;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Parecer de equivalencia pela REST API do Gemini (generateContent). Qualquer falha (rede, timeout, 429/5xx
 * apos 1 retry, JSON invalido ou fora do schema) cai no {@link AnalisadorFake}. Nunca registra a chave nem
 * o conteudo das ementas.
 */
@Component
@Primary
@ConditionalOnProperty(name = "ia.provedor", havingValue = "gemini")
public class GeminiAnalisador implements AnalisadorEquivalencia {

    private static final Logger log = LoggerFactory.getLogger(GeminiAnalisador.class);
    static final int LIMITE_JUSTIFICATIVA = 600;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient client;
    private final String modelo;
    private final long esperaRetryMs;
    private final AnalisadorFake fallback;

    @org.springframework.beans.factory.annotation.Autowired
    public GeminiAnalisador(@Value("${ia.gemini.api-key:}") String apiKey,
                            @Value("${ia.gemini.model:gemini-2.5-flash}") String modelo,
                            @Value("${ia.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
                            @Value("${ia.gemini.espera-retry-ms:2000}") long esperaRetryMs,
                            AnalisadorFake fallback) {
        this(apiKey, modelo, esperaRetryMs, fallback,
                RestClient.builder().baseUrl(baseUrl).requestFactory(fabrica()));
    }

    /** Usado pelos testes para injetar um MockRestServiceServer no builder. */
    GeminiAnalisador(String apiKey, String modelo, long esperaRetryMs, AnalisadorFake fallback,
                     RestClient.Builder builder) {
        if (apiKey == null || apiKey.isBlank())
            throw new IllegalStateException("ia.provedor=gemini exige a variavel GEMINI_API_KEY");
        this.modelo = modelo;
        this.esperaRetryMs = esperaRetryMs;
        this.fallback = fallback;
        this.client = builder.defaultHeader("x-goog-api-key", apiKey).build();
    }

    private static JdkClientHttpRequestFactory fabrica() {
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        f.setReadTimeout(TIMEOUT);
        return f;
    }

    @Override
    public ResultadoAnalise analisar(EntradaAnalise entrada) {
        String motivo;
        try {
            return chamarComRetry(entrada);
        } catch (FalhaGemini e) {
            motivo = e.getMessage();
        } catch (RuntimeException e) {
            motivo = "erro " + e.getClass().getSimpleName();
        }
        log.warn("Gemini indisponivel ({}); usando a heuristica local", motivo);
        ResultadoAnalise r = fallback.analisar(entrada);
        return new ResultadoAnalise(r.recomendacao(), r.similaridade(), r.justificativa(),
                AnalisadorFake.MODELO + " (fallback: " + motivo + ")");
    }

    private ResultadoAnalise chamarComRetry(EntradaAnalise entrada) {
        String corpo = JSON.writeValueAsString(montarRequisicao(entrada));
        String resposta;
        try {
            resposta = chamar(corpo);
        } catch (FalhaGemini e) {
            if (!e.repetivel)
                throw e;
            esperar();
            resposta = chamar(corpo);
        }
        return interpretar(resposta);
    }

    private String chamar(String corpo) {
        try {
            String resposta = client.post().uri("/v1beta/models/{modelo}:generateContent", modelo)
                    .contentType(MediaType.APPLICATION_JSON).body(corpo).retrieve().body(String.class);
            if (resposta == null)
                throw new FalhaGemini("resposta vazia", false);
            return resposta;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            throw new FalhaGemini("HTTP " + status, status == 429 || status >= 500);
        } catch (FalhaGemini e) {
            throw e;
        } catch (RuntimeException e) {
            // timeout/conexao (ResourceAccessException): tambem conta como falha transitoria
            throw new FalhaGemini("falha de rede/timeout", true);
        }
    }

    private void esperar() {
        try {
            Thread.sleep(esperaRetryMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private ResultadoAnalise interpretar(String resposta) {
        try {
            String texto = JSON.readTree(resposta).path("candidates").path(0).path("content")
                    .path("parts").path(0).path("text").asString(null);
            if (texto == null)
                throw new FalhaGemini("resposta sem conteudo", false);
            JsonNode json = JSON.readTree(texto);
            JsonNode rec = json.path("recomendacao");
            JsonNode sim = json.path("similaridade");
            JsonNode just = json.path("justificativa");
            if (!rec.isString() || !sim.isIntegralNumber() || !just.isString())
                throw new FalhaGemini("resposta fora do schema", false);
            RecomendacaoIa recomendacao;
            try {
                recomendacao = RecomendacaoIa.valueOf(rec.asString());
            } catch (IllegalArgumentException e) {
                throw new FalhaGemini("recomendacao invalida", false);
            }
            int similaridade = sim.asInt();
            if (similaridade < 0 || similaridade > 100)
                throw new FalhaGemini("similaridade fora de 0-100", false);
            String justificativa = just.asString().strip();
            if (justificativa.length() > LIMITE_JUSTIFICATIVA)
                justificativa = justificativa.substring(0, LIMITE_JUSTIFICATIVA);
            return new ResultadoAnalise(recomendacao, similaridade, justificativa, modelo);
        } catch (FalhaGemini e) {
            throw e;
        } catch (RuntimeException e) {
            throw new FalhaGemini("JSON invalido", false);
        }
    }

    static Map<String, Object> montarRequisicao(EntradaAnalise e) {
        Map<String, Object> schema = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "recomendacao", Map.of("type", "STRING", "enum",
                                List.of("SUGERE_DEFERIR", "SUGERE_INDEFERIR", "INCONCLUSIVO")),
                        "similaridade", Map.of("type", "INTEGER"),
                        "justificativa", Map.of("type", "STRING")),
                "required", List.of("recomendacao", "similaridade", "justificativa"));
        return Map.of(
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt(e))))),
                "generationConfig", Map.of("temperature", 0.2, "responseMimeType", "application/json",
                        "responseSchema", schema));
    }

    static String prompt(EntradaAnalise e) {
        return """
                Você é um assistente de coordenação acadêmica que analisa pedidos de aproveitamento de disciplinas.
                Compare a disciplina de origem (cursada pelo aluno) com a disciplina de destino (do curso).
                Critério: há equivalência quando a compatibilidade de conteúdo é de pelo menos 75%% e a carga horária é compatível.
                Responda somente em JSON com os campos:
                - recomendacao: SUGERE_DEFERIR (critério atendido), SUGERE_INDEFERIR (critério claramente não atendido) ou INCONCLUSIVO (dados insuficientes ou dúvida);
                - similaridade: inteiro de 0 a 100 com a compatibilidade de conteúdo;
                - justificativa: no máximo 600 caracteres, citando os tópicos cobertos e os que faltam.
                A recomendação é apenas um apoio; a decisão final é do professor.

                DISCIPLINA DE ORIGEM
                Nome: %s
                Carga horária: %s horas
                Ementa: %s

                DISCIPLINA DE DESTINO
                Nome: %s
                Carga horária: %s horas
                Ementa: %s
                """.formatted(v(e.disciplinaOrigem()), v(e.cargaHorariaOrigem()), v(e.ementaOrigem()),
                v(e.disciplinaDestino()), v(e.cargaHorariaDestino()), v(e.ementaDestino()));
    }

    private static String v(Object o) {
        return o == null ? "(não informado)" : o.toString();
    }

    private static final class FalhaGemini extends RuntimeException {
        final boolean repetivel;

        FalhaGemini(String motivo, boolean repetivel) {
            super(motivo, null, false, false);
            this.repetivel = repetivel;
        }
    }
}
