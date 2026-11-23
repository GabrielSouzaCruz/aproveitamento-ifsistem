package ifsuldeminas.edu.br.ifsystem.aproveitamento.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ExtratorTextoPdfTest {

    private static byte[] fixture(String nome) throws IOException {
        try (var in = ExtratorTextoPdfTest.class.getResourceAsStream("/pdf/" + nome)) {
            return in.readAllBytes();
        }
    }

    @Test
    void extraiOTextoDeUmPdfComTexto() throws IOException {
        String texto = ExtratorTextoPdf.extrair(fixture("pdf-com-texto.pdf"));

        assertThat(texto).contains("EMENTA - Banco de Dados I", "60 horas", "modelo relacional", "SQL");
    }

    @Test
    void pdfSemTextoDevolveStringVazia() throws IOException {
        assertThat(ExtratorTextoPdf.extrair(fixture("pdf-sem-texto.pdf"))).isEmpty();
    }

    @Test
    void pdfCorrompidoDevolveStringVaziaSemLancar() {
        assertThat(ExtratorTextoPdf.extrair("%PDF-1.7\nlixo".getBytes(StandardCharsets.US_ASCII))).isEmpty();
    }

    @Test
    void limparRemoveNulEAplicaOLimiteDe100MilCaracteres() {
        assertThat(ExtratorTextoPdf.limpar("  a\u0000b  ")).isEqualTo("ab");
        assertThat(ExtratorTextoPdf.limpar("x".repeat(150_000))).hasSize(ExtratorTextoPdf.LIMITE_CARACTERES);
        assertThat(ExtratorTextoPdf.limpar(null)).isEmpty();
    }
}
