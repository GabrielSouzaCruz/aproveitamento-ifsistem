package ifsuldeminas.edu.br.ifsystem.aproveitamento.service;

import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.StatusVerificacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.TipoDetectado;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.service.GeminiClient.FalhaGemini;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Verificacao de anexos pelo Gemini (via {@link GeminiClient}). Com texto extraido de 200 caracteres ou mais
 * envia o texto; senao envia o PDF em inline_data e pede a transcricao. Qualquer falha cai no
 * {@link VerificadorLocal}, com o motivo registrado no modelo.
 */
@Component
@Primary
@ConditionalOnProperty(name = "ia.provedor", havingValue = "gemini")
public class GeminiVerificadorAnexo implements VerificadorAnexo {

    private static final Logger log = LoggerFactory.getLogger(GeminiVerificadorAnexo.class);
    static final int MIN_CARACTERES_TEXTO = 200;
    static final int LIMITE_MOTIVO = 400;

    private final GeminiClient gemini;
    private final VerificadorLocal fallback;

    public GeminiVerificadorAnexo(GeminiClient gemini, VerificadorLocal fallback) {
        this.gemini = gemini;
        this.fallback = fallback;
    }

    @Override
    public ResultadoVerificacao verificar(EntradaVerificacao entrada) {
        String motivo;
        try {
            boolean enviarPdf = !temTextoSuficiente(entrada);
            return interpretar(gemini.gerarJson(montarRequisicao(entrada)), enviarPdf);
        } catch (FalhaGemini e) {
            motivo = e.getMessage();
        } catch (RuntimeException e) {
            motivo = "erro " + e.getClass().getSimpleName();
        }
        log.warn("Gemini indisponivel na verificacao do anexo ({}); usando o verificador local", motivo);
        ResultadoVerificacao r = fallback.verificar(entrada);
        return new ResultadoVerificacao(r.tipoDetectado(), r.status(), r.motivo(), r.cargaHorariaEncontrada(),
                null, VerificadorLocal.MODELO + " (fallback: " + motivo + ")");
    }

    static boolean temTextoSuficiente(EntradaVerificacao e) {
        return e.textoExtraido() != null && e.textoExtraido().strip().length() >= MIN_CARACTERES_TEXTO;
    }

    private ResultadoVerificacao interpretar(JsonNode json, boolean pdfEnviado) {
        JsonNode tipo = json.path("tipoDetectado");
        JsonNode status = json.path("status");
        JsonNode motivo = json.path("motivo");
        if (!tipo.isString() || !status.isString() || !motivo.isString())
            throw new FalhaGemini("resposta fora do schema", false);
        TipoDetectado tipoDetectado;
        StatusVerificacao statusVerificacao;
        try {
            tipoDetectado = TipoDetectado.valueOf(tipo.asString());
            statusVerificacao = StatusVerificacao.valueOf(status.asString());
        } catch (IllegalArgumentException e) {
            throw new FalhaGemini("valor de enum invalido", false);
        }
        if (statusVerificacao == StatusVerificacao.VERIFICANDO)
            throw new FalhaGemini("status invalido", false);
        String texto = motivo.asString().strip();
        if (texto.length() > LIMITE_MOTIVO)
            texto = texto.substring(0, LIMITE_MOTIVO);
        JsonNode carga = json.path("cargaHorariaEncontrada");
        Integer cargaEncontrada = carga.isIntegralNumber() ? carga.asInt() : null;
        JsonNode transcrito = json.path("textoTranscrito");
        String textoTranscrito = pdfEnviado && transcrito.isString()
                ? ExtratorTextoPdf.limpar(transcrito.asString()) : null;
        return new ResultadoVerificacao(tipoDetectado, statusVerificacao, texto, cargaEncontrada,
                textoTranscrito == null || textoTranscrito.isEmpty() ? null : textoTranscrito, gemini.modelo());
    }

    static Map<String, Object> montarRequisicao(EntradaVerificacao e) {
        boolean enviarPdf = !temTextoSuficiente(e);
        Map<String, Object> propriedades = new LinkedHashMap<>();
        propriedades.put("tipoDetectado", Map.of("type", "STRING", "enum", List.of("EMENTA", "HISTORICO", "OUTRO")));
        propriedades.put("status", Map.of("type", "STRING", "enum",
                List.of("VALIDO", "INVALIDO", "PRECISA_CONFERIR")));
        propriedades.put("motivo", Map.of("type", "STRING"));
        propriedades.put("cargaHorariaEncontrada", Map.of("type", "INTEGER", "nullable", true));
        List<String> obrigatorios = new ArrayList<>(List.of("tipoDetectado", "status", "motivo"));
        if (enviarPdf)
            propriedades.put("textoTranscrito", Map.of("type", "STRING"));
        Map<String, Object> schema = Map.of("type", "OBJECT", "properties", propriedades,
                "required", obrigatorios);

        List<Object> partes = new ArrayList<>();
        partes.add(Map.of("text", prompt(e, enviarPdf)));
        if (enviarPdf)
            partes.add(Map.of("inline_data", Map.of("mime_type", "application/pdf",
                    "data", Base64.getEncoder().encodeToString(e.pdf() == null ? new byte[0] : e.pdf()))));
        return Map.of(
                "contents", List.of(Map.of("role", "user", "parts", partes)),
                "generationConfig", Map.of("temperature", 0.1, "responseMimeType", "application/json",
                        "responseSchema", schema));
    }

    static String prompt(EntradaVerificacao e, boolean pdfAnexado) {
        String base = """
                Você é um assistente de coordenação acadêmica que confere os documentos anexados a pedidos de aproveitamento de disciplinas.
                O aluno informou a disciplina de origem abaixo e anexou um PDF que deveria ser a ementa dela ou o histórico escolar que a comprova.
                Verifique:
                1. o documento é uma ementa ou um histórico escolar (tipoDetectado: EMENTA, HISTORICO ou OUTRO);
                2. o nome da disciplina no documento bate com o informado;
                3. a carga horária do documento confere com a informada;
                4. o conteúdo é coerente com a ementa digitada pelo aluno.
                Responda somente em JSON com os campos:
                - tipoDetectado: EMENTA, HISTORICO ou OUTRO;
                - status: VALIDO (todos os critérios atendidos), INVALIDO (documento claramente errado ou sem relação com a disciplina) ou PRECISA_CONFERIR (dúvida, dados ilegíveis ou critérios parcialmente atendidos);
                - motivo: no máximo 400 caracteres, dizendo quais critérios foram ou não atendidos;
                - cargaHorariaEncontrada: inteiro com a carga horária da disciplina no documento, ou null se não houver;
                %s
                A verificação é apenas um apoio; a conferência final é humana.

                DISCIPLINA DE ORIGEM INFORMADA PELO ALUNO
                Nome: %s
                Instituição: %s
                Carga horária: %s horas
                Ementa digitada: %s
                """.formatted(pdfAnexado
                        ? "- textoTranscrito: transcrição fiel do texto do PDF anexado (no máximo 100000 caracteres);"
                        : "",
                v(e.disciplina()), v(e.instituicao()), v(e.cargaHoraria()), v(e.ementa()));
        if (pdfAnexado)
            return base + "\nO documento está anexado como PDF.";
        return base + "\nTEXTO EXTRAÍDO DO PDF\n" + e.textoExtraido().strip();
    }

    private static String v(Object o) {
        return o == null ? "(não informado)" : o.toString();
    }
}
