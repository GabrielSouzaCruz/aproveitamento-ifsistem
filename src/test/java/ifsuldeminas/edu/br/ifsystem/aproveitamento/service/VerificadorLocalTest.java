package ifsuldeminas.edu.br.ifsystem.aproveitamento.service;

import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.StatusVerificacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.domain.TipoDetectado;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.service.VerificadorAnexo.EntradaVerificacao;
import ifsuldeminas.edu.br.ifsystem.aproveitamento.service.VerificadorAnexo.ResultadoVerificacao;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VerificadorLocalTest {

    private static final String EMENTA = "Modelagem de dados, modelo relacional, linguagem SQL e normalizacao";
    private final VerificadorLocal verificador = new VerificadorLocal();

    private ResultadoVerificacao verificar(String disciplina, Integer carga, String ementa, String texto) {
        return verificador.verificar(new EntradaVerificacao(disciplina, "UFLA", carga, ementa, texto, null));
    }

    @Test
    void semTextoPrecisaConferirComoPossivelPdfEscaneado() {
        ResultadoVerificacao r = verificar("Banco de Dados I", 60, EMENTA, "   ");

        assertThat(r.status()).isEqualTo(StatusVerificacao.PRECISA_CONFERIR);
        assertThat(r.motivo()).isEqualTo("PDF sem texto, possivelmente escaneado");
        assertThat(r.tipoDetectado()).isEqualTo(TipoDetectado.OUTRO);
        assertThat(r.modelo()).isEqualTo("verificador-local-v1");
        assertThat(verificar("Banco de Dados I", 60, EMENTA, null).status())
                .isEqualTo(StatusVerificacao.PRECISA_CONFERIR);
    }

    @Test
    void ementaComNomeECargaEncontradosEValida() {
        String texto = "EMENTA\nDisciplina: Banco de Dados I - Carga horária: 60 horas\n"
                + "Conteúdo programático: modelagem de dados, modelo relacional, SQL e normalização.";

        ResultadoVerificacao r = verificar("banco de dados i", 60, EMENTA, texto);

        assertThat(r.status()).isEqualTo(StatusVerificacao.VALIDO);
        assertThat(r.tipoDetectado()).isEqualTo(TipoDetectado.EMENTA);
        assertThat(r.motivo()).contains("nome da disciplina encontrado", "60h encontrada");
    }

    @Test
    void historicoComNomeECoerenciaComAEmentaEValido() {
        String texto = "Histórico escolar. Banco de Dados I - aprovado. Modelagem de dados, modelo relacional,"
                + " linguagem SQL e normalizacao.";

        ResultadoVerificacao r = verificar("Banco de Dados I", 80, EMENTA, texto);

        assertThat(r.status()).isEqualTo(StatusVerificacao.VALIDO);
        assertThat(r.tipoDetectado()).isEqualTo(TipoDetectado.HISTORICO);
        assertThat(r.motivo()).contains("80h não encontrada");
    }

    @Test
    void documentoSemRelacaoComADisciplinaEInvalido() {
        String texto = "Nota fiscal de compra de material de escritorio, canetas e papel sulfite, total R$ 120,00";

        ResultadoVerificacao r = verificar("Banco de Dados I", 60, EMENTA, texto);

        assertThat(r.status()).isEqualTo(StatusVerificacao.INVALIDO);
        assertThat(r.tipoDetectado()).isEqualTo(TipoDetectado.OUTRO);
        assertThat(r.motivo()).contains("não encontrado", "60h não encontrada");
    }

    @Test
    void nomeEncontradoMasCargaEConteudoDivergentesPrecisaConferir() {
        String texto = "Ementa de Banco de Dados I. Carga horária: 30 horas. Fisica quantica e relatividade.";

        ResultadoVerificacao r = verificar("Banco de Dados I", 60, EMENTA, texto);

        assertThat(r.status()).isEqualTo(StatusVerificacao.PRECISA_CONFERIR);
        assertThat(r.tipoDetectado()).isEqualTo(TipoDetectado.EMENTA);
    }

    @Test
    void cargaNaoCasaComNumeroMaior() {
        // "160" nao conta como "60"
        ResultadoVerificacao r = verificar("Banco de Dados I", 60, null, "Ementa de Banco de Dados I, 160 horas");

        assertThat(r.status()).isEqualTo(StatusVerificacao.PRECISA_CONFERIR);
        assertThat(r.motivo()).contains("60h não encontrada");
    }

    @Test
    void jaccardIgnoraAcentosEStopwords() {
        assertThat(VerificadorLocal.jaccard("Modelagem relacional e SQL", "modelagem relacional, SQL")).isEqualTo(100);
        assertThat(VerificadorLocal.jaccard("alfa beta gama", "delta epsilon zeta")).isZero();
        assertThat(VerificadorLocal.jaccard("alfa beta", null)).isZero();
    }
}
