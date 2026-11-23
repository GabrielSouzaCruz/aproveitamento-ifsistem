package ifsuldeminas.edu.br.ifsystem.aproveitamento.service;

import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.RecomendacaoIa;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Analisador deterministico (provedor fake e reserva do Gemini): compara as ementas por
 * sobreposicao de palavras (indice de Jaccard, 0-100) e considera a diferenca de carga horaria.
 */
@Component
public class AnalisadorFake implements AnalisadorEquivalencia {

    static final String MODELO = "heuristica-local-v1";
    static final int LIMITE_DEFERIR = 70;
    static final int LIMITE_INDEFERIR = 40;
    static final int LIMITE_DIFERENCA_CARGA = 25;
    private static final int MAX_TERMOS_NA_JUSTIFICATIVA = 15;

    private static final Set<String> STOPWORDS = Set.of(
            "a", "o", "as", "os", "um", "uma", "uns", "umas", "de", "do", "da", "dos", "das", "em", "no", "na",
            "nos", "nas", "por", "para", "pra", "com", "sem", "sob", "sobre", "entre", "ate", "ao", "aos", "e",
            "ou", "mas", "que", "se", "como", "mais", "menos", "ser", "sao", "foi", "ha", "ja", "seu", "sua",
            "seus", "suas", "este", "esta", "estes", "estas", "esse", "essa", "esses", "essas", "isso", "isto",
            "aquele", "aquela", "tambem", "quando", "onde", "pelo", "pela", "pelos", "pelas", "num", "numa",
            "cada", "bem", "assim", "alem", "apos", "durante", "tais", "tal", "todo", "toda", "todos", "todas",
            "outro", "outra", "outros", "outras", "ainda", "partir", "visando", "atraves", "etc");

    @Override
    public ResultadoAnalise analisar(EntradaAnalise entrada) {
        Set<String> origem = termos(entrada.ementaOrigem());
        Set<String> destino = termos(entrada.ementaDestino());
        Double diferencaCarga = diferencaCarga(entrada.cargaHorariaOrigem(), entrada.cargaHorariaDestino());
        String carga = descreverCarga(entrada, diferencaCarga);

        if (origem.isEmpty() || destino.isEmpty()) {
            return new ResultadoAnalise(RecomendacaoIa.INCONCLUSIVO, 0,
                    "Ementa ausente em uma das disciplinas: nao ha como comparar o conteudo. " + carga, MODELO);
        }

        Set<String> comuns = new TreeSet<>(origem);
        comuns.retainAll(destino);
        Set<String> uniao = new TreeSet<>(origem);
        uniao.addAll(destino);
        int similaridade = (int) Math.round(100.0 * comuns.size() / uniao.size());

        RecomendacaoIa recomendacao = similaridade >= LIMITE_DEFERIR ? RecomendacaoIa.SUGERE_DEFERIR
                : similaridade < LIMITE_INDEFERIR ? RecomendacaoIa.SUGERE_INDEFERIR
                : RecomendacaoIa.INCONCLUSIVO;
        boolean cargaDivergente = diferencaCarga != null && diferencaCarga > LIMITE_DIFERENCA_CARGA;
        if (cargaDivergente && recomendacao == RecomendacaoIa.SUGERE_DEFERIR)
            recomendacao = RecomendacaoIa.INCONCLUSIVO;

        String justificativa = "Similaridade de ementas: " + similaridade + "% (" + comuns.size() + " de "
                + uniao.size() + " termos distintos em comum" + listar(comuns) + "). " + carga
                + (cargaDivergente ? " Carga horaria diverge mais de " + LIMITE_DIFERENCA_CARGA
                + "%: a recomendacao fica limitada a inconclusivo." : "");
        return new ResultadoAnalise(recomendacao, similaridade, justificativa, MODELO);
    }

    /** Minusculas, sem acentos, sem pontuacao e sem stopwords; palavras com menos de 3 letras sao descartadas. */
    static Set<String> termos(String texto) {
        Set<String> termos = new TreeSet<>();
        if (texto == null)
            return termos;
        String limpo = Normalizer.normalize(texto.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        for (String palavra : limpo.split("[^a-z0-9]+")) {
            if (palavra.length() >= 3 && !STOPWORDS.contains(palavra))
                termos.add(palavra);
        }
        return termos;
    }

    /** Diferenca percentual em relacao a carga do destino; nula se alguma das cargas for desconhecida. */
    private static Double diferencaCarga(Integer origem, Integer destino) {
        if (origem == null || destino == null || destino <= 0)
            return null;
        return 100.0 * Math.abs(origem - destino) / destino;
    }

    private static String descreverCarga(EntradaAnalise e, Double diferenca) {
        if (diferenca == null)
            return "Carga horaria nao comparada (dado ausente).";
        return String.format(Locale.ROOT, "Carga horaria: %dh cursadas contra %dh exigidas (diferenca de %.0f%%).",
                e.cargaHorariaOrigem(), e.cargaHorariaDestino(), diferenca);
    }

    private static String listar(Set<String> comuns) {
        if (comuns.isEmpty())
            return "";
        String lista = String.join(", ", comuns.stream().limit(MAX_TERMOS_NA_JUSTIFICATIVA).toList());
        return ": " + lista + (comuns.size() > MAX_TERMOS_NA_JUSTIFICATIVA ? ", ..." : "");
    }
}
