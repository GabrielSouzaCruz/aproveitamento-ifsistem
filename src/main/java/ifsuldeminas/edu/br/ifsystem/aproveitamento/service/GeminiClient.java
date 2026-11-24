package ifsuldeminas.edu.br.ifsystem.aproveitamento.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Cliente HTTP e configuracao comuns da REST API do Gemini (generateContent), usados pelo parecer de
 * equivalencia e pela verificacao de anexos. Faz 1 retry em 429/5xx/timeout. Nunca registra a chave nem
 * o conteudo enviado.
 */
@Component
@ConditionalOnProperty(name = "ia.provedor", havingValue = "gemini")
public class GeminiClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    static final JsonMapper JSON = JsonMapper.builder().build();
    /** Sem charset explicito o Spring codificaria/decodificaria como ISO-8859-1 e os acentos se perderiam. */
    private static final MediaType JSON_UTF8 = new MediaType("application", "json", StandardCharsets.UTF_8);

    private final RestClient client;
    private final String modelo;
    private final long esperaRetryMs;

    @Autowired
    public GeminiClient(@Value("${ia.gemini.api-key:}") String apiKey,
                        @Value("${ia.gemini.model:gemini-2.5-flash}") String modelo,
                        @Value("${ia.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
                        @Value("${ia.gemini.espera-retry-ms:2000}") long esperaRetryMs) {
        this(apiKey, modelo, esperaRetryMs, RestClient.builder().baseUrl(baseUrl).requestFactory(fabrica()));
    }

    /** Usado pelos testes para injetar um MockRestServiceServer no builder. */
    GeminiClient(String apiKey, String modelo, long esperaRetryMs, RestClient.Builder builder) {
        if (apiKey == null || apiKey.isBlank())
            throw new IllegalStateException("ia.provedor=gemini exige a variavel GEMINI_API_KEY");
        this.modelo = modelo;
        this.esperaRetryMs = esperaRetryMs;
        this.client = builder.defaultHeader("x-goog-api-key", apiKey).build();
    }

    private static JdkClientHttpRequestFactory fabrica() {
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        f.setReadTimeout(TIMEOUT);
        return f;
    }

    public String modelo() {
        return modelo;
    }

    /**
     * Envia a requisicao e devolve o JSON produzido pelo modelo (o texto do primeiro candidato, ja
     * interpretado). Lanca {@link FalhaGemini} em qualquer falha, depois de 1 retry nas transitorias.
     */
    JsonNode gerarJson(Map<String, Object> requisicao) {
        String corpo = JSON.writeValueAsString(requisicao);
        String resposta;
        try {
            resposta = chamar(corpo);
        } catch (FalhaGemini e) {
            if (!e.repetivel)
                throw e;
            esperar();
            resposta = chamar(corpo);
        }
        return extrairJson(resposta);
    }

    private String chamar(String corpo) {
        try {
            byte[] bytes = client.post().uri("/v1beta/models/{modelo}:generateContent", modelo)
                    .contentType(JSON_UTF8).body(corpo.getBytes(StandardCharsets.UTF_8)).retrieve().body(byte[].class);
            if (bytes == null)
                throw new FalhaGemini("resposta vazia", false);
            return new String(bytes, StandardCharsets.UTF_8); // UTF-8 explicito, independente do Content-Type da resposta
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

    private static JsonNode extrairJson(String resposta) {
        try {
            String texto = JSON.readTree(resposta).path("candidates").path(0).path("content")
                    .path("parts").path(0).path("text").asString(null);
            if (texto == null)
                throw new FalhaGemini("resposta sem conteudo", false);
            return JSON.readTree(texto);
        } catch (FalhaGemini e) {
            throw e;
        } catch (RuntimeException e) {
            throw new FalhaGemini("JSON invalido", false);
        }
    }

    /** Falha ao falar com o Gemini; {@code repetivel} indica erro transitorio (429, 5xx, rede). */
    static final class FalhaGemini extends RuntimeException {
        final boolean repetivel;

        FalhaGemini(String motivo, boolean repetivel) {
            super(motivo, null, false, false);
            this.repetivel = repetivel;
        }
    }
}
