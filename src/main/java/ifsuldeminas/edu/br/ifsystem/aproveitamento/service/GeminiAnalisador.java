package ifsuldeminas.edu.br.ifsystem.aproveitamento.service;

import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.RecomendacaoIa;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.service.GeminiClient.FalhaGemini;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * Parecer de equivalencia pelo Gemini (via {@link GeminiClient}). Qualquer falha (rede, timeout, 429/5xx
 * apos 1 retry, JSON invalido ou fora do schema) cai no {@link AnalisadorFake}. Nunca registra a chave nem
 * o conteudo das ementas.
 */
@Component
@Primary
@ConditionalOnProperty(name = "ia.provedor", havingValue = "gemini")
public class GeminiAnalisador implements AnalisadorEquivalencia {

    private static final Logger log = LoggerFactory.getLogger(GeminiAnalisador.class);
    static final int LIMITE_JUSTIFICATIVA = 600;

    private final GeminiClient gemini;
    private final AnalisadorFake fallback;

    public GeminiAnalisador(GeminiClient gemini, AnalisadorFake fallback) {
        this.gemini = gemini;
        this.fallback = fallback;
    }

    @Override
    public ResultadoAnalise analisar(EntradaAnalise entrada) {
        String motivo;
        try {
            return interpretar(gemini.gerarJson(montarRequisicao(entrada)));
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

    private ResultadoAnalise interpretar(JsonNode json) {
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
        return new ResultadoAnalise(recomendacao, similaridade, justificativa, gemini.modelo());
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
}
