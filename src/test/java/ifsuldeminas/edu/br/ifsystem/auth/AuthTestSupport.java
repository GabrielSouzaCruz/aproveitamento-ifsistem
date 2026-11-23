package ifsuldeminas.edu.br.ifsystem.auth;

import ifsuldeminas.edu.br.ifsystem.auth.service.JwtService;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.usuario.domain.Usuario;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

/** Helper de teste: usuarios em memoria e tokens JWT validos para o header Authorization. */
public final class AuthTestSupport {
    private AuthTestSupport() {
    }

    public static Usuario usuario(UUID id, Perfil perfil) {
        Usuario u = new Usuario("Fulano " + perfil, "52998224725", "fulano@ifsuldeminas.edu.br", "MAT-" + id,
                "hash", null, null, null, perfil);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    public static String bearer(JwtService jwt, UUID id, Perfil perfil) {
        return "Bearer " + jwt.gerar(usuario(id, perfil)).token();
    }

    /** CPF valido aleatorio (so digitos), para testes que nao podem colidir. */
    public static String cpfAleatorio() {
        var r = java.util.concurrent.ThreadLocalRandom.current();
        int[] d = new int[11];
        do {
            for (int i = 0; i < 9; i++) d[i] = r.nextInt(10);
        } while (java.util.Arrays.stream(d, 0, 9).distinct().count() == 1);
        for (int n = 9; n < 11; n++) {
            int soma = 0;
            for (int i = 0; i < n; i++) soma += d[i] * (n + 1 - i);
            d[n] = (soma * 10 % 11) % 10;
        }
        StringBuilder sb = new StringBuilder();
        for (int x : d) sb.append(x);
        return sb.toString();
    }
}
