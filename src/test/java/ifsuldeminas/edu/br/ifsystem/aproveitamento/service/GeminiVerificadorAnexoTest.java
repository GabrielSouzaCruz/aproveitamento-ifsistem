package ifsuldeminas.edu.br.ifsystem.aproveitamento.service;

import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.StatusVerificacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.TipoDetectado;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.service.VerificadorAnexo.EntradaVerificacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.service.VerificadorAnexo.ResultadoVerificacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiVerificadorAnexoTest {

    private static final String URL = "https://gemini.test/v1beta/models/gemini-teste:generateContent";
    private static final String TEXTO_LONGO = ("Ementa da disciplina Banco de Dados I. Carga horaria de 60 horas. "
            + "Conteudo programatico: modelagem de dados, modelo relacional, SQL, normalizacao e transacoes. ")
            .repeat(3);
    private static final byte[] PDF = "%PDF-1.7 conteudo ficticio".getBytes(StandardCharsets.US_ASCII);
    private static final String OK = envelope("{\\\"tipoDetectado\\\":\\\"EMENTA\\\",\\\"status\\\":\\\"VALIDO\\\","
            + "\\\"motivo\\\":\\\"Nome e carga conferem.\\\",\\\"cargaHorariaEncontrada\\\":60}");

    private MockRestServiceServer server;
    private GeminiVerificadorAnexo verificador;

    private static String envelope(String textoEscapado) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + textoEscapado + "\"}]}}]}";
    }

    private static EntradaVerificacao comTexto() {
        return new EntradaVerificacao("Banco de Dados I", "UFLA", 60, "Modelagem e SQL", TEXTO_LONGO, PDF);
    }

    private static EntradaVerificacao semTexto() {
        return new EntradaVerificacao("Banco de Dados I", "UFLA", 60, "Modelagem e SQL", "", PDF);
    }

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://gemini.test");
        server = MockRestServiceServer.bindTo(builder).build();
        verificador = new GeminiVerificadorAnexo(new GeminiClient("chave-ficticia", "gemini-teste", 0, builder),
                new VerificadorLocal());
    }

    @Test
    void comTextoSuficienteEnviaOTextoENaoOPdf() {
        server.expect(once(), requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "chave-ficticia"))
                .andExpect(jsonPath("$.contents[0].parts", hasSize(1)))
                .andExpect(jsonPath("$.contents[0].parts[0].text", containsString("TEXTO EXTRAÍDO DO PDF")))
                .andExpect(jsonPath("$.contents[0].parts[0].text", containsString("modelo relacional")))
                .andExpect(jsonPath("$.contents[0].parts[0].text", containsString("Nome: Banco de Dados I")))
                .andExpect(jsonPath("$.contents[0].parts[0].text", containsString("Instituição: UFLA")))
                .andExpect(jsonPath("$.contents[0].parts[0].text", containsString("Carga horária: 60 horas")))
                .andExpect(jsonPath("$.contents[0].parts[0].text", containsString("Ementa digitada: Modelagem e SQL")))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.generationConfig.responseSchema.required.length()").value(3))
                .andExpect(jsonPath("$.generationConfig.responseSchema.properties.textoTranscrito").doesNotExist())
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        ResultadoVerificacao r = verificador.verificar(comTexto());

        assertThat(r.status()).isEqualTo(StatusVerificacao.VALIDO);
        assertThat(r.tipoDetectado()).isEqualTo(TipoDetectado.EMENTA);
        assertThat(r.motivo()).isEqualTo("Nome e carga conferem.");
        assertThat(r.cargaHorariaEncontrada()).isEqualTo(60);
        assertThat(r.textoTranscrito()).isNull();
        assertThat(r.modelo()).isEqualTo("gemini-teste");
        server.verify();
    }

    @Test
    void semTextoEnviaOPdfEmInlineDataESalvaATranscricao() {
        String base64 = Base64.getEncoder().encodeToString(PDF);
        String resposta = envelope("{\\\"tipoDetectado\\\":\\\"HISTORICO\\\",\\\"status\\\":\\\"PRECISA_CONFERIR\\\","
                + "\\\"motivo\\\":\\\"Carga ilegivel.\\\",\\\"cargaHorariaEncontrada\\\":null,"
                + "\\\"textoTranscrito\\\":\\\"Historico escolar Banco de Dados I\\\"}");
        server.expect(once(), requestTo(URL))
                .andExpect(jsonPath("$.contents[0].parts", hasSize(2)))
                .andExpect(jsonPath("$.contents[0].parts[1].inline_data.mime_type").value("application/pdf"))
                .andExpect(jsonPath("$.contents[0].parts[1].inline_data.data").value(base64))
                .andExpect(jsonPath("$.contents[0].parts[0].text", containsString("textoTranscrito")))
                .andExpect(jsonPath("$.generationConfig.responseSchema.properties.textoTranscrito.type")
                        .value("STRING"))
                .andRespond(withSuccess(resposta, MediaType.APPLICATION_JSON));

        ResultadoVerificacao r = verificador.verificar(semTexto());

        assertThat(r.status()).isEqualTo(StatusVerificacao.PRECISA_CONFERIR);
        assertThat(r.tipoDetectado()).isEqualTo(TipoDetectado.HISTORICO);
        assertThat(r.cargaHorariaEncontrada()).isNull();
        assertThat(r.textoTranscrito()).isEqualTo("Historico escolar Banco de Dados I");
        server.verify();
    }

    @Test
    void textoCurtoDe199CaracteresTambemEnviaOPdf() {
        EntradaVerificacao curto = new EntradaVerificacao("BD", "UFLA", 60, "x", "a".repeat(199), PDF);
        EntradaVerificacao limite = new EntradaVerificacao("BD", "UFLA", 60, "x", "a".repeat(200), PDF);

        assertThat(GeminiVerificadorAnexo.temTextoSuficiente(curto)).isFalse();
        assertThat(GeminiVerificadorAnexo.temTextoSuficiente(limite)).isTrue();
    }

    @Test
    void promptTrazOsCriteriosEOContexto() {
        String prompt = GeminiVerificadorAnexo.prompt(comTexto(), false);

        assertThat(prompt).contains("ementa ou um histórico escolar", "nome da disciplina", "carga horária",
                "coerente com a ementa digitada", "400 caracteres", "Banco de Dados I");
    }

    @Test
    void acentosDoMotivoChegamIntactos() {
        server.expect(once(), requestTo(URL)).andRespond(withSuccess(envelope(
                "{\\\"tipoDetectado\\\":\\\"EMENTA\\\",\\\"status\\\":\\\"VALIDO\\\",\\\"motivo\\\":"
                        + "\\\"Conteúdo compatível; carga horária confere.\\\"}"), MediaType.APPLICATION_JSON));

        ResultadoVerificacao r = verificador.verificar(comTexto());

        assertThat(r.motivo()).isEqualTo("Conteúdo compatível; carga horária confere.");
        assertThat(r.modelo()).isEqualTo("gemini-teste");
    }

    @Test
    void promptPedeAcentuacaoCorreta() {
        assertThat(GeminiVerificadorAnexo.prompt(comTexto(), false))
                .contains("português do Brasil com acentuação correta");
    }

    @Test
    void motivoLongoETruncadoEm400() {
        String longo = "a".repeat(700);
        server.expect(once(), requestTo(URL)).andRespond(withSuccess(envelope(
                "{\\\"tipoDetectado\\\":\\\"OUTRO\\\",\\\"status\\\":\\\"INVALIDO\\\",\\\"motivo\\\":\\\""
                        + longo + "\\\"}"), MediaType.APPLICATION_JSON));

        ResultadoVerificacao r = verificador.verificar(comTexto());

        assertThat(r.motivo()).hasSize(400);
        assertThat(r.cargaHorariaEncontrada()).isNull();
    }

    @Test
    void erro429SeguidoDeSucessoFazRetry() {
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(once(), requestTo(URL)).andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        assertThat(verificador.verificar(comTexto()).modelo()).isEqualTo("gemini-teste");
        server.verify();
    }

    @Test
    void erro500DuasVezesCaiNoFallbackLocalComOModeloRegistrado() {
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        ResultadoVerificacao r = verificador.verificar(comTexto());

        assertThat(r.modelo()).isEqualTo("verificador-local-v1 (fallback: HTTP 500)");
        assertThat(r.status()).isNotNull().isNotEqualTo(StatusVerificacao.VERIFICANDO);
        assertThat(r.textoTranscrito()).isNull();
        server.verify();
    }

    @Test
    void erro4xxNaoFazRetryECaiNoFallback() {
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        ResultadoVerificacao r = verificador.verificar(semTexto());

        assertThat(r.modelo()).isEqualTo("verificador-local-v1 (fallback: HTTP 403)");
        assertThat(r.status()).isEqualTo(StatusVerificacao.PRECISA_CONFERIR);
        assertThat(r.motivo()).isEqualTo("PDF sem texto, possivelmente escaneado");
        server.verify();
    }

    @Test
    void jsonInvalidoEForaDoSchemaCaemNoFallback() {
        server.expect(once(), requestTo(URL))
                .andRespond(withSuccess(envelope("isto nao e json"), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(URL)).andRespond(withSuccess(envelope(
                "{\\\"tipoDetectado\\\":\\\"TALVEZ\\\",\\\"status\\\":\\\"VALIDO\\\",\\\"motivo\\\":\\\"x\\\"}"),
                MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(URL)).andRespond(withSuccess(envelope(
                "{\\\"tipoDetectado\\\":\\\"EMENTA\\\",\\\"status\\\":\\\"VERIFICANDO\\\",\\\"motivo\\\":\\\"x\\\"}"),
                MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(URL)).andRespond(withSuccess(envelope(
                "{\\\"status\\\":\\\"VALIDO\\\"}"), MediaType.APPLICATION_JSON));

        assertThat(verificador.verificar(comTexto()).modelo()).contains("fallback: JSON invalido");
        assertThat(verificador.verificar(comTexto()).modelo()).contains("fallback: valor de enum invalido");
        assertThat(verificador.verificar(comTexto()).modelo()).contains("fallback: status invalido");
        assertThat(verificador.verificar(comTexto()).modelo()).contains("fallback: resposta fora do schema");
    }

    @Test
    void contextoComGeminiTemOVerificadorGeminiComoPrimario() {
        new ApplicationContextRunner()
                .withBean(AnalisadorFake.class)
                .withBean(VerificadorLocal.class)
                .withUserConfiguration(GeminiClient.class, GeminiAnalisador.class, GeminiVerificadorAnexo.class)
                .withPropertyValues("ia.provedor=gemini", "ia.gemini.api-key=chave-ficticia")
                .run(contexto -> assertThat(contexto.getBean(VerificadorAnexo.class))
                        .isInstanceOf(GeminiVerificadorAnexo.class));
    }

    @Test
    void provedorFakeUsaOVerificadorLocal() {
        new ApplicationContextRunner()
                .withBean(VerificadorLocal.class)
                .withUserConfiguration(GeminiClient.class, GeminiVerificadorAnexo.class)
                .run(contexto -> assertThat(contexto.getBean(VerificadorAnexo.class))
                        .isInstanceOf(VerificadorLocal.class));
    }
}
