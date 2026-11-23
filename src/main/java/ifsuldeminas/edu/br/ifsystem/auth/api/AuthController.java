package ifsuldeminas.edu.br.ifsystem.auth.api;

import ifsuldeminas.edu.br.ifsystem.auth.api.AuthDtos.Cadastro;
import ifsuldeminas.edu.br.ifsystem.auth.api.AuthDtos.Login;
import ifsuldeminas.edu.br.ifsystem.auth.api.AuthDtos.LoginResposta;
import ifsuldeminas.edu.br.ifsystem.auth.service.AuthService;
import ifsuldeminas.edu.br.ifsystem.usuario.api.UsuarioDtos.Resposta;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/cadastro")
    public ResponseEntity<Resposta> cadastro(@RequestBody Cadastro dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Resposta.de(auth.cadastrar(dados)));
    }

    @PostMapping("/login")
    public LoginResposta login(@RequestBody Login dados) {
        return auth.login(dados);
    }
}
