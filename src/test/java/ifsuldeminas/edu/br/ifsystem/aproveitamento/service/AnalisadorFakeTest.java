package ifsuldeminas.edu.br.ifsystem.aproveitamento.service;

import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.RecomendacaoIa;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.service.AnalisadorEquivalencia.EntradaAnalise;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.service.AnalisadorEquivalencia.ResultadoAnalise;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalisadorFakeTest {

    private static final String EMENTA = "Modelagem de dados, normalização de tabelas e linguagem SQL. "
            + "Transações e controle de concorrência em bancos de dados relacionais.";

    private final AnalisadorFake analisador = new AnalisadorFake();

    private static EntradaAnalise entrada(String ementaOrigem, int cargaOrigem, String ementaDestino, int cargaDestino) {
        return new EntradaAnalise("Origem", ementaOrigem, cargaOrigem, "Destino", ementaDestino, cargaDestino);
    }

    @Test
    void ementasIguaisSugeremDeferirComSimilaridadeMaxima() {
        ResultadoAnalise r = analisador.analisar(entrada(EMENTA, 60, EMENTA, 60));

        assertThat(r.similaridade()).isEqualTo(100);
        assertThat(r.recomendacao()).isEqualTo(RecomendacaoIa.SUGERE_DEFERIR);
        assertThat(r.modelo()).isEqualTo("heuristica-local-v1");
    }

    @Test
    void normalizaCaixaAcentosPontuacaoEStopwords() {
        ResultadoAnalise r = analisador.analisar(entrada(
                "MODELAGEM de Dados; NORMALIZACAO das tabelas!", 60,
                "modelagem, dados e normalização (tabelas)", 60));

        assertThat(r.similaridade()).isEqualTo(100);
        assertThat(r.justificativa()).contains("dados", "modelagem", "normalizacao", "tabelas")
                .doesNotContain(" de,", " das", " e,");
    }

    @Test
    void ementasDiferentesSugeremIndeferir() {
        ResultadoAnalise r = analisador.analisar(entrada(EMENTA, 60,
                "Historia da arte renascentista, pintura a oleo e escultura em marmore.", 60));

        assertThat(r.similaridade()).isLessThan(40);
        assertThat(r.recomendacao()).isEqualTo(RecomendacaoIa.SUGERE_INDEFERIR);
    }

    @Test
    void similaridadeIntermediariaEInconclusiva() {
        // 2 termos em comum de 4 distintos = 50
        ResultadoAnalise r = analisador.analisar(entrada("banco dados modelagem", 60, "banco dados redes", 60));

        assertThat(r.similaridade()).isEqualTo(50);
        assertThat(r.recomendacao()).isEqualTo(RecomendacaoIa.INCONCLUSIVO);
    }

    @Test
    void limitesDeCorteSaoInclusivosEmSetentaEExclusivosEmQuarenta() {
        // 7 de 10 = 70 -> deferir
        ResultadoAnalise setenta = analisador.analisar(entrada(
                "alfa bravo charlie delta echo foxtrot golf", 60,
                "alfa bravo charlie delta echo foxtrot golf hotel india juliet", 60));
        assertThat(setenta.similaridade()).isEqualTo(70);
        assertThat(setenta.recomendacao()).isEqualTo(RecomendacaoIa.SUGERE_DEFERIR);

        // 2 de 5 = 40 -> inconclusivo (nao e menor que 40)
        ResultadoAnalise quarenta = analisador.analisar(entrada("alfa bravo charlie", 60, "alfa bravo delta echo", 60));
        assertThat(quarenta.similaridade()).isEqualTo(40);
        assertThat(quarenta.recomendacao()).isEqualTo(RecomendacaoIa.INCONCLUSIVO);
    }

    @Test
    void cargaHorariaDivergenteLimitaAInconclusivo() {
        ResultadoAnalise r = analisador.analisar(entrada(EMENTA, 40, EMENTA, 60));

        assertThat(r.similaridade()).isEqualTo(100);
        assertThat(r.recomendacao()).isEqualTo(RecomendacaoIa.INCONCLUSIVO);
        assertThat(r.justificativa()).contains("40h", "60h", "33%");
    }

    @Test
    void diferencaDeCargaDeExatamente25PorCentoNaoLimita() {
        ResultadoAnalise r = analisador.analisar(entrada(EMENTA, 45, EMENTA, 60));

        assertThat(r.recomendacao()).isEqualTo(RecomendacaoIa.SUGERE_DEFERIR);
    }

    @Test
    void cargaDivergenteNaoAmenizaIndeferimento() {
        ResultadoAnalise r = analisador.analisar(entrada(EMENTA, 20, "Pintura renascentista e escultura", 60));

        assertThat(r.recomendacao()).isEqualTo(RecomendacaoIa.SUGERE_INDEFERIR);
    }

    @Test
    void justificativaListaTermosEmComumEDiferencaDeCarga() {
        ResultadoAnalise r = analisador.analisar(entrada(EMENTA, 60, EMENTA, 60));

        assertThat(r.justificativa()).contains("sql", "concorrencia", "Carga horaria", "diferenca de 0%");
    }

    @Test
    void ementaAusenteEInconclusiva() {
        ResultadoAnalise r = analisador.analisar(entrada(null, 60, EMENTA, 60));

        assertThat(r.recomendacao()).isEqualTo(RecomendacaoIa.INCONCLUSIVO);
        assertThat(r.similaridade()).isZero();
        assertThat(r.justificativa()).contains("Ementa ausente");
    }

    @Test
    void ignoraEntradaVazia() {
        ResultadoAnalise r = analisador.analisar(new EntradaAnalise(null, null, null, null, null, null));

        assertThat(r.recomendacao()).isEqualTo(RecomendacaoIa.INCONCLUSIVO);
        assertThat(r.modelo()).isEqualTo("heuristica-local-v1");
    }

    @Test
    void ehDeterministico() {
        EntradaAnalise e = entrada(EMENTA, 60, "Modelagem de dados e SQL", 60);

        assertThat(analisador.analisar(e)).isEqualTo(analisador.analisar(e));
    }
}
