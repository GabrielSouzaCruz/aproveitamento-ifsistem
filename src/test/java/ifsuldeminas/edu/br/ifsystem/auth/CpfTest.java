package ifsuldeminas.edu.br.ifsystem.auth;

import ifsuldeminas.edu.br.ifsystem.usuario.domain.Cpf;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CpfTest {
    @Test
    void aceitaCpfValidoComESemMascara() {
        assertThat(Cpf.valido("529.982.247-25")).isTrue();
        assertThat(Cpf.valido("52998224725")).isTrue();
        assertThat(Cpf.normalizar("529.982.247-25")).isEqualTo("52998224725");
    }

    @Test
    void rejeitaDigitoVerificadorErradoRepetidosETamanho() {
        assertThat(Cpf.valido("52998224726")).isFalse();
        assertThat(Cpf.valido("11111111111")).isFalse();
        assertThat(Cpf.valido("123")).isFalse();
        assertThat(Cpf.valido(null)).isFalse();
    }

    @Test
    void cpfsDoSeedDemoSaoValidos() {
        assertThat(Cpf.valido("52998224725")).isTrue();
        assertThat(Cpf.valido("11144477735")).isTrue();
        assertThat(Cpf.valido("12345678909")).isTrue();
    }
}
