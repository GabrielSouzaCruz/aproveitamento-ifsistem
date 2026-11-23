package ifsuldeminas.edu.br.ifsystem.aproveitamento.service;

import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.RecomendacaoIa;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.service.AnalisadorEquivalencia.EntradaAnalise;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.service.AnalisadorEquivalencia.ResultadoAnalise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiAnalisadorTest {

    private static final String URL = "https://gemini.test/v1beta/models/gemini-teste:generateContent";
    private static final String OK = envelope(
            "{\\\"recomendacao\\\":\\\"SUGERE_DEFERIR\\\",\\\"similaridade\\\":82,"
                    + "\\\"justificativa\\\":\\\"Cobre SQL e modelagem; falta apenas NoSQL.\\\"}");
    private static final EntradaAnalise ENTRADA = new EntradaAnalise(
            "Banco de Dados", "Modelagem de dados, SQL e transacoes", 60,
            "Banco de Dados I", "Modelagem de dados, SQL, transacoes e NoSQL", 60);

    private MockRestServiceServer server;
    private GeminiAnalisador analisador;

    private static String envelope(String textoEscapado) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + textoEscapado + "\"}]}}]}";
    }

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://gemini.test");
        server = MockRestServiceServer.bindTo(builder).build();
        analisador = new GeminiAnalisador(new GeminiClient("chave-ficticia", "gemini-teste", 0, builder),
                new AnalisadorFake());
    }

    @Test
    void respostaOkEMapeadaEGravaOModeloDoGemini() {
        server.expect(once(), requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "chave-ficticia"))
                .andExpect(jsonPath("$.generationConfig.temperature").value(0.2))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.generationConfig.responseSchema.required.length()").value(3))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        ResultadoAnalise r = analisador.analisar(ENTRADA);

        assertThat(r.recomendacao()).isEqualTo(RecomendacaoIa.SUGERE_DEFERIR);
        assertThat(r.similaridade()).isEqualTo(82);
        assertThat(r.justificativa()).contains("falta apenas NoSQL");
        assertThat(r.modelo()).isEqualTo("gemini-teste");
        server.verify();
    }

    @Test
    void promptTrazOsDadosDasDuasDisciplinas() {
        String prompt = GeminiAnalisador.prompt(ENTRADA);

        assertThat(prompt).contains("assistente de coordenação acadêmica", "75%", "600 caracteres",
                "Banco de Dados I", "Modelagem de dados, SQL e transacoes", "60 horas");
    }

    @Test
    void erro429SeguidoDeSucessoFazRetry() {
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(once(), requestTo(URL)).andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        ResultadoAnalise r = analisador.analisar(ENTRADA);

        assertThat(r.modelo()).isEqualTo("gemini-teste");
        assertThat(r.similaridade()).isEqualTo(82);
        server.verify();
    }

    @Test
    void erro500DuasVezesCaiNoFallback() {
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        ResultadoAnalise r = analisador.analisar(ENTRADA);

        assertThat(r.modelo()).isEqualTo("heuristica-local-v1 (fallback: HTTP 500)");
        assertThat(r.recomendacao()).isNotNull();
        server.verify();
    }

    @Test
    void erro4xxNaoFazRetryECaiNoFallback() {
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        ResultadoAnalise r = analisador.analisar(ENTRADA);

        assertThat(r.modelo()).isEqualTo("heuristica-local-v1 (fallback: HTTP 403)");
        server.verify();
    }

    @Test
    void jsonInvalidoCaiNoFallbackSemRetry() {
        server.expect(once(), requestTo(URL))
                .andRespond(withSuccess(envelope("isto nao e json"), MediaType.APPLICATION_JSON));

        ResultadoAnalise r = analisador.analisar(ENTRADA);

        assertThat(r.modelo()).startsWith("heuristica-local-v1 (fallback: ").contains("JSON invalido");
        server.verify();
    }

    @Test
    void foraDoSchemaCaiNoFallback() {
        String foraDoIntervalo = envelope("{\\\"recomendacao\\\":\\\"SUGERE_DEFERIR\\\",\\\"similaridade\\\":150,"
                + "\\\"justificativa\\\":\\\"x\\\"}");
        String recomendacaoDesconhecida = envelope("{\\\"recomendacao\\\":\\\"TALVEZ\\\",\\\"similaridade\\\":50,"
                + "\\\"justificativa\\\":\\\"x\\\"}");
        server.expect(once(), requestTo(URL)).andRespond(withSuccess(foraDoIntervalo, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(URL))
                .andRespond(withSuccess(recomendacaoDesconhecida, MediaType.APPLICATION_JSON));

        assertThat(analisador.analisar(ENTRADA).modelo()).contains("fallback: similaridade fora de 0-100");
        assertThat(analisador.analisar(ENTRADA).modelo()).contains("fallback: recomendacao invalida");
    }

    @Test
    void justificativaLongaETruncadaEm600() {
        String longa = "a".repeat(900);
        server.expect(once(), requestTo(URL)).andRespond(withSuccess(envelope(
                "{\\\"recomendacao\\\":\\\"INCONCLUSIVO\\\",\\\"similaridade\\\":60,\\\"justificativa\\\":\\\""
                        + longa + "\\\"}"), MediaType.APPLICATION_JSON));

        assertThat(analisador.analisar(ENTRADA).justificativa()).hasSize(600);
    }

    @Test
    void semChaveComProvedorGeminiOContextoFalha() {
        new ApplicationContextRunner()
                .withBean(AnalisadorFake.class)
                .withUserConfiguration(GeminiClient.class, GeminiAnalisador.class)
                .withPropertyValues("ia.provedor=gemini", "ia.gemini.api-key=")
                .run(contexto -> {
                    assertThat(contexto).hasFailed();
                    assertThat(contexto.getStartupFailure()).hasRootCauseMessage(
                            "ia.provedor=gemini exige a variavel GEMINI_API_KEY");
                });
    }

    @Test
    void comChaveOContextoSobeEGeminiEPrimario() {
        new ApplicationContextRunner()
                .withBean(AnalisadorFake.class)
                .withUserConfiguration(GeminiClient.class, GeminiAnalisador.class)
                .withPropertyValues("ia.provedor=gemini", "ia.gemini.api-key=chave-ficticia")
                .run(contexto -> assertThat(contexto.getBean(AnalisadorEquivalencia.class))
                        .isInstanceOf(GeminiAnalisador.class));
    }

    @Test
    void provedorFakePorPadraoNaoCriaOGemini() {
        new ApplicationContextRunner()
                .withBean(AnalisadorFake.class)
                .withUserConfiguration(GeminiClient.class, GeminiAnalisador.class)
                .run(contexto -> assertThat(contexto).doesNotHaveBean(GeminiAnalisador.class));
    }
}
