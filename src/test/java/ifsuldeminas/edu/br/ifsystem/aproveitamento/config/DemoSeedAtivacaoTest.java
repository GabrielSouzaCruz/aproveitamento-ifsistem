package ifsuldeminas.edu.br.ifsystem.aproveitamento.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DemoSeedAtivacaoTest {

    private boolean ativo(MockEnvironment env) {
        ConditionContext ctx = mock(ConditionContext.class);
        when(ctx.getEnvironment()).thenReturn(env);
        return new DemoSeed.Ativado().matches(ctx, null);
    }

    @Test
    void desligadoSemProfileDemoENemDemoSeed() {
        assertThat(ativo(new MockEnvironment())).isFalse();
    }

    @Test
    void desligadoNoProfileRenderPorPadrao() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("render");
        assertThat(ativo(env)).isFalse();
    }

    @Test
    void ligadoNoProfileDemo() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("demo");
        assertThat(ativo(env)).isTrue();
    }

    @Test
    void ligadoComDemoSeedTrueMesmoNoRender() {
        MockEnvironment env = new MockEnvironment().withProperty("app.demo-seed", "true");
        env.setActiveProfiles("render");
        assertThat(ativo(env)).isTrue();
    }

    @Test
    void demoSeedFalseMantemDesligado() {
        assertThat(ativo(new MockEnvironment().withProperty("app.demo-seed", "false"))).isFalse();
    }
}
