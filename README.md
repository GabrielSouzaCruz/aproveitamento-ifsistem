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

O login é o login único do IFSISTEM (`ifsistem-auth`): abra http://localhost:8080, clique em "Entrar com IFSISTEM" e entre com CPF e senha no auth. Para rodar local, suba o auth na porta 9000 (veja o README dele) e use `AUTH_ISSUER=http://localhost:9000`. O seed demo (profile `demo` ou `DEMO_SEED=true`) **não cria usuários**: usa os IDs fixos abaixo, que são os dos usuários demo já existentes no auth (vieram da migração).

| Perfil | ID no auth | Observação |
|---|---|---|
| `ALUNO` | `11111111-1111-1111-1111-111111111111` | dona das 3 solicitações |
| `SERVIDOR` | `22222222-2222-2222-2222-222222222222` | secretaria |
| `PROFESSOR` | `33333333-3333-3333-3333-333333333333` | coordenadora do curso TADS (catálogo com 4 disciplinas e ementas) |

| Solicitação | ID | Status |
|---|---|---|
| Engenharia de Software | `aaaaaaaa-0000-0000-0000-000000000001` | `RASCUNHO` |
| Banco de Dados | `aaaaaaaa-0000-0000-0000-000000000002` | `ENVIADA`, com parecer da IA |
| Algoritmos e Lógica de Programação | `aaaaaaaa-0000-0000-0000-000000000003` | `DEFERIDA` |

### Autenticação

- O backend é *resource server*: valida o JWT (RS256) emitido pelo `ifsistem-auth` (assinatura e emissor, `AUTH_ISSUER`) e transforma a claim `perfil` em `ROLE_<PERFIL>`. Não há mais login, cadastro, senha nem JWT próprios aqui; a gestão de usuários é no auth (API `/usuarios` dele).
- `public.usuarios` é só uma cópia de leitura (nomes nas telas e checagem de PROFESSOR ativo ao vincular coordenador). Na primeira requisição de um `sub` desconhecido, o registro é criado/atualizado a partir das claims (`sub`, `nome`, `cpf`, `perfil`).
- Coordenadores: a tela lista os professores já sincronizados e permite buscar por CPF; a busca faz `GET {AUTH}/usuarios?perfil=PROFESSOR` com o token do próprio SERVIDOR e sincroniza o encontrado. Se o auth recusar, o sistema responde 502 (não há contorno).
- Front: `js/sso.js` lê `GET /config` (`authUrl`, `clientId=aproveitamento`), carrega `{authUrl}/ifsistem-sso.js` e configura o `IfsistemSSO`. `callback.html` conclui o login; o botão Sair faz o logout no auth.
- Exemplo via API: `curl -H "Authorization: Bearer $ACCESS_TOKEN" http://localhost:8080/aproveitamentos` (o access token vem do fluxo do auth).

### Postgres (opcional)

O profile padrão usa Postgres, configurado por `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` (os defaults são `ifsystem`, `postgres` e `123`). Para usar o `docker-compose.yml`:
```
docker compose up -d
DB_URL=jdbc:postgresql://localhost:5432/aproveitamento DB_USERNAME=aproveitamento DB_PASSWORD=aproveitamento ./mvnw spring-boot:run
```

## Produção

Profile `render` (Postgres externo, schema via Flyway). Variáveis de ambiente:

| Variável | Obrigatória | Descrição |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | sim | Conexão JDBC com o Postgres (ex.: `jdbc:postgresql://host:5432/postgres`). |
| `AUTH_ISSUER` | sim (tem padrão) | URL do `ifsistem-auth`; padrão `https://ifsistem-auth.onrender.com`. |
| `DEMO_SEED` | não | `true` popula curso, catálogo e solicitações de demonstração (os usuários vêm do auth). Padrão no profile `render`: `false`. |

**Desligar a demo:** deixe `DEMO_SEED` ausente ou `false` (é o padrão no `render.yaml`). Desligar não apaga dados já semeados.

**Keep-alive:** `GET /health` é público e responde `200 {"status":"ok"}` sem consultar o banco; use-o em pings para evitar a hibernação do plano free.

## Deploy

Deploy no Render (Blueprint) usando o profile `render`, com Postgres externo (Supabase). O seed de demonstração **não** roda por padrão no profile `render`; só com `DEMO_SEED=true` (veja "Produção").

1. No Supabase, em **Connect**, copie a string do **Session pooler** (porta `5432`, compatível com IPv4) e converta para o formato JDBC, com `?sslmode=require`:
   ```
   DB_URL=jdbc:postgresql://aws-0-<regiao>.pooler.supabase.com:5432/postgres?sslmode=require
   DB_USERNAME=postgres.<project-ref>
   DB_PASSWORD=<senha do banco>
   ```
2. No Render, clique em **New > Blueprint**, selecione este repositório (o `render.yaml` na raiz é lido automaticamente) e preencha as variáveis do banco (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`). `AUTH_ISSUER` já vem no Blueprint. As `redirectUris` do cliente `aproveitamento` precisam estar cadastradas no auth.
3. Após o build, a aplicação responde em `https://<servico>.onrender.com` (Swagger em `/swagger-ui.html`; health check e keep-alive em `/health`). No plano free o serviço hiberna após inatividade e a primeira requisição é lenta.

## Migrações de banco (Flyway)

O schema do Postgres (profiles padrão e `render`) é versionado com Flyway em `src/main/resources/db/migration`; o Hibernate só **valida** (`ddl-auto=validate`) e não altera mais o banco. A `V1__schema_inicial.sql` cria todas as tabelas.

**Daqui pra frente, toda mudança de entidade (coluna, tabela, constraint, índice) exige uma nova migração `V{n}__descricao.sql`** (ex.: `V2__add_coluna_x.sql`). Nunca edite uma migração já aplicada. Sem a migração, a aplicação não sobe (falha na validação).

O profile `demo` (H2) e os testes usam `ddl-auto=create-drop` com o Flyway desligado, pois o SQL das migrações é específico do Postgres.

## Parecer de equivalência por IA (Gemini)

O parecer de cada solicitação enviada é gerado de forma assíncrona. Por padrão usa a heurística local
(`heuristica-local-v1`); com o Gemini ativado, ela vira reserva automática.

| Variável | Padrão | Descrição |
|---|---|---|
| `IA_PROVEDOR` | `fake` | `fake` (heurística local) ou `gemini` |
| `GEMINI_API_KEY` | — | Obrigatória com `IA_PROVEDOR=gemini` (sem ela a aplicação não sobe) |
| `GEMINI_MODEL` | `gemini-2.5-flash` | Modelo Flash disponível no free tier |

**Gerar a chave:** acesse https://aistudio.google.com/apikey, entre com a conta Google, clique em
*Create API key* e copie o valor. Nunca versione a chave; defina-a só como variável de ambiente.

```
IA_PROVEDOR=gemini GEMINI_API_KEY=<sua-chave> ./mvnw spring-boot:run
```

Comportamento: chamada REST `POST /v1beta/models/{modelo}:generateContent` (chave no header
`x-goog-api-key`, temperature 0.2, saída JSON com schema), timeout de 30s e 1 retry em 429/5xx. Se ainda
falhar, ou se o JSON vier inválido, usa a heurística local e grava o modelo como
`heuristica-local-v1 (fallback: <motivo>)`. A chave e o conteúdo das ementas nunca são logados.
`ParecerIa.modelo` guarda o modelo do Gemini efetivamente usado. A antiga `ifconnect.analisador` foi removida.
