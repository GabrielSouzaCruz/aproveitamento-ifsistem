# Aproveitamento de Disciplinas
## API
### Primeira Conexão
URL https://ifconnect-api.vercel.app
PAYLOAD /api
TOKEN i2KRCWgS1OHuivC8jDz1fFkuGFllHtqcEzkL7UEu6xc3ksWwkOw3FIn52yB4Wx63Se93ChoX0I8ezhmeFH04blhZdNZmxe0U0ne8WXZ6Z68DcRecZEPHqvrRarIZ__Bjps0xCzwncUo

- Python
``` 
import requests

token = "i2KRCWgS1OHuivC8jDz1fFkuGFllHtqcEzkL7UEu6xc3ksWwkOw3FIn52yB4Wx63Se93ChoX0I8ezhmeFH04blhZdNZmxe0U0ne8WXZ6Z68DcRecZEPHqvrRarIZ__Bjps0xCzwncUo"

response = requests.get("https://ifconnect-api.vercel.app/api", headers={"Authorization": f"Bearer {token}"})

print(response.status_code)
print(response.json())
```

- Java
``` 
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Main {
    public static void main(String[] args) {
        String token = "i2KRCWgS1OHuivC8jDz1fFkuGFllHtqcEzkL7UEu6xc3ksWwkOw3FIn52yB4Wx63Se93ChoX0I8ezhmeFH04blhZdNZmxe0U0ne8WXZ6Z68DcRecZEPHqvrRarIZ__Bjps0xCzwncUo";

        try {
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://ifconnect-api.vercel.app/api"))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            System.out.println("Status: " + response.statusCode());
            System.out.println("Resposta: " + response.body());

        } catch (Exception e) {
            System.out.println("Erro na requisição: " + e.getMessage());
        }
    }
}
```

## Demo

Roteiro para apresentar a aplicação sem Docker: o profile `demo` usa H2 em arquivo (`./data/demo`) e popula dados de exemplo na primeira execução. Ao reiniciar, os dados continuam lá e o seed não roda de novo. Para começar do zero, apague a pasta `data/`.

1. Subir a aplicação (JDK 21+):
   ```
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
   ```
   No Windows/PowerShell: `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"`
2. Abrir o Swagger: http://localhost:8080/swagger-ui.html
3. (Opcional) Console H2: http://localhost:8080/h2-console. JDBC URL `jdbc:h2:file:./data/demo`, usuário `sa`, senha vazia.

A identidade é provisória e vem dos headers `X-Usuario-Id` e `X-Perfil`. Use os usuários abaixo. A senha de todos é `demo123`.

| Perfil | Nome | X-Usuario-Id | Observação |
|---|---|---|---|
| `ALUNO` | Ana Aluna Demo | `11111111-1111-1111-1111-111111111111` | curso ADS, dono das 3 solicitações |
| `SERVIDOR` | Sergio Servidor Demo | `22222222-2222-2222-2222-222222222222` | |
| `PROFESSOR` | Paula Professora Demo | `33333333-3333-3333-3333-333333333333` | coordenadora do curso ADS |

| Solicitação | ID | Status |
|---|---|---|
| Engenharia de Software | `aaaaaaaa-0000-0000-0000-000000000001` | `RASCUNHO` |
| Banco de Dados | `aaaaaaaa-0000-0000-0000-000000000002` | `ENVIADA`, com parecer da IA |
| Algoritmos e Lógica de Programação | `aaaaaaaa-0000-0000-0000-000000000003` | `DEFERIDA` |

Exemplos:
```
curl -H "X-Usuario-Id: 11111111-1111-1111-1111-111111111111" -H "X-Perfil: ALUNO" http://localhost:8080/aproveitamentos
curl -H "X-Usuario-Id: 22222222-2222-2222-2222-222222222222" -H "X-Perfil: SERVIDOR" http://localhost:8080/aproveitamentos/aaaaaaaa-0000-0000-0000-000000000002/parecer-ia
```

### Postgres (opcional)

O profile padrão usa Postgres, configurado por `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` (os defaults são `ifsystem`, `postgres` e `123`). Para usar o `docker-compose.yml`:
```
docker compose up -d
DB_URL=jdbc:postgresql://localhost:5432/aproveitamento DB_USERNAME=aproveitamento DB_PASSWORD=aproveitamento ./mvnw spring-boot:run
```

## Deploy

Deploy no Render (Blueprint) usando o profile `render`, com Postgres externo (Supabase). O profile `render` também executa o seed de demonstração (idempotente) na primeira subida.

1. No Supabase, em **Connect**, copie a string do **Session pooler** (porta `5432`, compatível com IPv4) e converta para o formato JDBC, com `?sslmode=require`:
   ```
   DB_URL=jdbc:postgresql://aws-0-<regiao>.pooler.supabase.com:5432/postgres?sslmode=require
   DB_USERNAME=postgres.<project-ref>
   DB_PASSWORD=<senha do banco>
   ```
2. No Render, clique em **New > Blueprint**, selecione este repositório (o `render.yaml` na raiz é lido automaticamente) e preencha as 3 variáveis de ambiente do banco: `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`.
3. Após o build, a aplicação responde em `https://<servico>.onrender.com` (Swagger em `/swagger-ui.html`; health check em `/v3/api-docs`). No plano free o serviço hiberna após inatividade e a primeira requisição é lenta.
