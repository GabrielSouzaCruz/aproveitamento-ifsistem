package ifsuldeminas.edu.br.ifsystem.aproveitamento.service;

import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.StatusVerificacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.TipoDetectado;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Verificador deterministico (provedor fake e reserva do Gemini): procura o nome da disciplina, a carga
 * horaria e as palavras "ementa", "conteudo programatico" e "historico" no texto extraido, e mede a
 * sobreposicao de palavras (Jaccard, 0-100) com a ementa digitada pelo aluno.
 */
@Component
public class VerificadorLocal implements VerificadorAnexo {

    static final String MODELO = "verificador-local-v1";
    static final String MOTIVO_SEM_TEXTO = "PDF sem texto, possivelmente escaneado";
    /** Jaccard minimo para considerar o conteudo coerente com a ementa digitada. */
    static final int LIMITE_JACCARD_COERENTE = 15;
    /** Abaixo disto o conteudo nao tem relacao com a ementa digitada. */
    static final int LIMITE_JACCARD_SEM_RELACAO = 5;

    @Override
    public ResultadoVerificacao verificar(EntradaVerificacao e) {
        if (e.textoExtraido() == null || e.textoExtraido().isBlank())
            return new ResultadoVerificacao(TipoDetectado.OUTRO, StatusVerificacao.PRECISA_CONFERIR,
                    MOTIVO_SEM_TEXTO, null, null, MODELO);

        String texto = normalizar(e.textoExtraido());
        boolean temEmenta = texto.contains("ementa") || texto.contains("conteudo programatico");
        boolean temHistorico = texto.contains("historico");
        TipoDetectado tipo = temEmenta ? TipoDetectado.EMENTA
                : temHistorico ? TipoDetectado.HISTORICO : TipoDetectado.OUTRO;

        String nome = e.disciplina() == null ? "" : normalizar(e.disciplina());
        boolean nomeEncontrado = !nome.isBlank() && texto.contains(nome);
        boolean cargaEncontrada = e.cargaHoraria() != null && contemNumero(texto, e.cargaHoraria());
        int jaccard = jaccard(e.textoExtraido(), e.ementa());
        boolean coerente = jaccard >= LIMITE_JACCARD_COERENTE;

        StatusVerificacao status;
        if (tipo != TipoDetectado.OUTRO && nomeEncontrado && (cargaEncontrada || coerente))
            status = StatusVerificacao.VALIDO;
        else if (!nomeEncontrado && !cargaEncontrada && jaccard < LIMITE_JACCARD_SEM_RELACAO)
            status = StatusVerificacao.INVALIDO;
        else
            status = StatusVerificacao.PRECISA_CONFERIR;

        String motivo = "Documento " + descrever(tipo) + "; nome da disciplina "
                + (nomeEncontrado ? "encontrado" : "não encontrado") + "; carga horária "
                + (e.cargaHoraria() == null ? "não informada"
                : cargaEncontrada ? e.cargaHoraria() + "h encontrada" : e.cargaHoraria() + "h não encontrada")
                + "; similaridade com a ementa digitada: " + jaccard + "%.";
        return new ResultadoVerificacao(tipo, status, motivo, null, null, MODELO);
    }

    private static String descrever(TipoDetectado tipo) {
        return switch (tipo) {
            case EMENTA -> "parece uma ementa";
            case HISTORICO -> "parece um histórico";
            case OUTRO -> "sem as palavras ementa, conteúdo programático ou histórico";
        };
    }

    /** Indice de Jaccard (0-100) entre os termos do texto do PDF e os da ementa digitada; 0 sem ementa. */
    static int jaccard(String texto, String ementa) {
        Set<String> a = AnalisadorFake.termos(texto);
        Set<String> b = AnalisadorFake.termos(ementa);
        if (a.isEmpty() || b.isEmpty())
            return 0;
        Set<String> comuns = new TreeSet<>(a);
        comuns.retainAll(b);
        Set<String> uniao = new TreeSet<>(a);
        uniao.addAll(b);
        return (int) Math.round(100.0 * comuns.size() / uniao.size());
    }

    private static boolean contemNumero(String texto, int numero) {
        return Pattern.compile("(?<![0-9])" + numero + "(?![0-9])").matcher(texto).find();
    }

    private static String normalizar(String s) {
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replaceAll("\\s+", " ").strip();
    }
}
