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

O login é real: abra http://localhost:8080 e entre com CPF e senha (ou crie uma conta de aluno em "Criar conta"). A senha dos usuários demo é `demo1234` (só nos profiles `demo` e `render`).

| Perfil | Nome | CPF | Observação |
|---|---|---|---|
| `ALUNO` | Ana Aluna Demo | `529.982.247-25` | curso ADS, dona das 3 solicitações |
| `SERVIDOR` | Sergio Servidor Demo | `111.444.777-35` | secretaria |
| `PROFESSOR` | Paula Professora Demo | `123.456.789-09` | coordenadora do curso TADS (catálogo com 4 disciplinas e ementas) |

| Solicitação | ID | Status |
|---|---|---|
| Engenharia de Software | `aaaaaaaa-0000-0000-0000-000000000001` | `RASCUNHO` |
| Banco de Dados | `aaaaaaaa-0000-0000-0000-000000000002` | `ENVIADA`, com parecer da IA |
| Algoritmos e Lógica de Programação | `aaaaaaaa-0000-0000-0000-000000000003` | `DEFERIDA` |

Exemplo via API (o token vai em `Authorization: Bearer`):
```
TOKEN=$(curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json'   -d '{"cpf":"52998224725","senha":"demo1234"}' | sed 's/.*"token":"\([^"]*\)".*//')
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/aproveitamentos
```

### Autenticação

- `POST /auth/cadastro` (público): cria sempre um ALUNO. `POST /auth/login`: devolve `{token, expiraEm, usuario}`; o token (JWT HS256) vale 8 h.
- Secretaria (perfil `SERVIDOR`): `POST /usuarios` (PROFESSOR ou SERVIDOR), `GET /usuarios?perfil=`, `PATCH /usuarios/{id}/ativo`.
- Variáveis: `JWT_SECRET` (obrigatória no profile `render`, mínimo 32 caracteres), `ADMIN_CPF`, `ADMIN_SENHA` e `ADMIN_EMAIL` (opcional). Se não existir nenhum SERVIDOR e `ADMIN_CPF`/`ADMIN_SENHA` estiverem definidos, a conta "Secretaria" é criada na inicialização.

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
2. No Render, clique em **New > Blueprint**, selecione este repositório (o `render.yaml` na raiz é lido automaticamente) e preencha as variáveis do banco (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) e da conta inicial da secretaria (`ADMIN_CPF`, `ADMIN_SENHA`, opcional `ADMIN_EMAIL`). `JWT_SECRET` é gerado pelo Blueprint.
3. Após o build, a aplicação responde em `https://<servico>.onrender.com` (Swagger em `/swagger-ui.html`; health check em `/v3/api-docs`). No plano free o serviço hiberna após inatividade e a primeira requisição é lenta.

## Migrações de banco (Flyway)

O schema do Postgres (profiles padrão e `render`) é versionado com Flyway em `src/main/resources/db/migration`; o Hibernate só **valida** (`ddl-auto=validate`) e não altera mais o banco. A `V1__schema_inicial.sql` cria todas as tabelas.

**Daqui pra frente, toda mudança de entidade (coluna, tabela, constraint, índice) exige uma nova migração `V{n}__descricao.sql`** (ex.: `V2__add_coluna_x.sql`). Nunca edite uma migração já aplicada. Sem a migração, a aplicação não sobe (falha na validação).

O profile `demo` (H2) e os testes usam `ddl-auto=create-drop` com o Flyway desligado, pois o SQL das migrações é específico do Postgres.
