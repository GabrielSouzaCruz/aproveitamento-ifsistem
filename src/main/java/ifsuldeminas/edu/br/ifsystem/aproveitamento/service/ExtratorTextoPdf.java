package ifsuldeminas.edu.br.ifsystem.aproveitamento.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/** Extrai o texto de um PDF com o PDFBox. PDF sem texto (escaneado) ou ilegivel devolve string vazia. */
public final class ExtratorTextoPdf {
    private static final Logger log = LoggerFactory.getLogger(ExtratorTextoPdf.class);
    public static final int LIMITE_CARACTERES = 100_000;

    private ExtratorTextoPdf() {
    }

    public static String extrair(byte[] pdf) {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return limpar(stripper.getText(documento));
        } catch (IOException | RuntimeException e) {
            log.warn("Nao foi possivel extrair o texto do PDF ({})", e.getClass().getSimpleName());
            return "";
        }
    }

    /** Remove NUL (o Postgres rejeita em colunas text), apara e limita a {@value #LIMITE_CARACTERES} caracteres. */
    static String limpar(String texto) {
        if (texto == null)
            return "";
        String limpo = texto.replace("\u0000", "").strip();
        return limpo.length() > LIMITE_CARACTERES ? limpo.substring(0, LIMITE_CARACTERES) : limpo;
    }
}
