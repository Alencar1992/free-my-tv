# Configurar seu feed de inscrições

O APK público funciona sem servidor. Este guia é para quem quer acrescentar os cards com vídeos de suas inscrições do YouTube. Você vai precisar de um computador ou servidor na mesma rede da TV, Python 3, um projeto Google Cloud e um novo build do APK com a URL do serviço.

O fluxo é: **sua conta autoriza leitura → seu servidor consulta o YouTube → sua TV recebe o feed**. A TV não recebe a chave de API, o cliente OAuth ou o refresh token. Cada pessoa configura sua própria conta e seu próprio serviço.

## 1. Preparar o projeto e as credenciais

No [Google Cloud Console](https://console.cloud.google.com/), crie ou escolha um projeto seu e habilite a **YouTube Data API v3**. Crie uma chave de API para as consultas públicas do serviço e restrinja seu uso à API necessária e, quando viável, ao ambiente do servidor. Veja a [introdução oficial da YouTube Data API](https://developers.google.com/youtube/data/getting-started).

Configure o consentimento OAuth e crie um cliente do tipo **Desktop app**. Se o projeto estiver em modo de teste, inclua a conta que vai autorizar como usuário de teste. Baixe o JSON do cliente para uma pasta privada no computador onde você abrirá o navegador. O bootstrap usa um callback local, PKCE e o escopo `https://www.googleapis.com/auth/youtube.readonly`, conforme o [fluxo OAuth para aplicativos desktop](https://developers.google.com/identity/protocols/oauth2/native-app).

Prepare uma pasta fora do repositório. Os comandos a seguir usam Bash em macOS/Linux/WSL e um nome de exemplo:

```sh
umask 077
mkdir -p "$HOME/.config/free-my-tv"
chmod 700 "$HOME/.config/free-my-tv"
```

Salve o JSON baixado como `client-secret.json` nessa pasta, com permissão `600`. Guarde a chave de API em um arquivo privado `server.env`, que será lido somente pelo serviço:

```text
GOOGLE_API_KEY=SUA_CHAVE_DE_API
SUBSCRIPTIONS_CREDENTIALS=/caminho/privado/subscriptions-oauth.json
```

Substitua os valores com um editor local e aplique `chmod 600` ao arquivo. O segundo caminho deve ser o caminho absoluto do arquivo OAuth **na máquina que executará o servidor**. Não use credenciais de outra ferramenta ou da instalação de outra pessoa. Não coloque esses valores em commits, issues, screenshots ou no APK.

## 2. Autorizar as inscrições

Na raiz do clone, execute no computador onde o navegador será aberto:

```sh
python3 tools/authorize_subscriptions.py \
  --client "$HOME/.config/free-my-tv/client-secret.json" \
  --output "$HOME/.config/free-my-tv/subscriptions-oauth.json" \
  --url-file "$HOME/.config/free-my-tv/authorization-url.json"
```

O processo fica esperando o consentimento. Abra localmente o arquivo `authorization-url.json` e a URL contida nele, usando um navegador no **mesmo computador**. Escolha a identidade do YouTube cujas inscrições você deseja acompanhar. Contas pessoais e canais de marca podem ter inscrições diferentes; confira a identidade antes de consentir.

O programa aceita somente o escopo de leitura indicado e salva `subscriptions-oauth.json` com permissão `600`. Ele não sobrescreve um arquivo existente. Para reautorizar, use novos nomes de saída, confira o resultado e depois substitua a credencial do serviço. A autorização tem prazo de 30 minutos para ser concluída.

Se o servidor estiver em outra máquina, transfira `subscriptions-oauth.json` por um canal privado, como SSH, e mantenha a pasta com permissão `700` e o arquivo com `600`, pertencentes ao usuário que executará o serviço. O JSON do cliente e o arquivo da URL não precisam ficar no servidor. O refresh token é uma credencial de acesso à conta e precisa de armazenamento privado.

Em projetos externos no estado **Testing**, autorizações com escopos como o do YouTube podem gerar refresh tokens que expiram após sete dias. Revogação, limites e outras condições também podem exigir novo consentimento; consulte a [documentação de expiração do Google](https://developers.google.com/identity/protocols/oauth2#expiration). Não há promessa de autorização permanente.

## 3. Rodar o serviço localmente

O backend usa a biblioteca padrão do Python. Na máquina que o executará, mantenha o repositório e os arquivos privados separados. A chave de API pública é exigida pelo backend atual mesmo quando as métricas estão ocultas na TV.

Primeiro, faça um teste acessível apenas nessa máquina. Ajuste o caminho do arquivo privado se necessário:

```sh
umask 077
mkdir -p "$HOME/.local/state/free-my-tv"
chmod 700 "$HOME/.local/state/free-my-tv"
set -a
. "$HOME/.config/free-my-tv/server.env"
set +a
python3 tools/channel_feed.py \
  --host 127.0.0.1 \
  --port 3330 \
  --cache "$HOME/.local/state/free-my-tv/channel.json" \
  --subscriptions-cache "$HOME/.local/state/free-my-tv/subscriptions.json"
```

Carregue com `source` apenas um arquivo criado por você: ele é interpretado pelo shell. Se usar um gerenciador de serviço, forneça as mesmas variáveis de ambiente por um arquivo privado e configure um usuário dedicado.

Em outro terminal:

```sh
curl http://127.0.0.1:3330/health
```

`/health` identifica o serviço e o estado das recomendações. `loading` pode aparecer durante a primeira coleta; `authorization_required` pede verificar credencial, permissões e consentimento. `GET /channel` fornece os dados do feed. Ele contém informações das suas inscrições, portanto evite publicá-lo em logs ou tickets.

Para a TV alcançar o serviço, encerre **essa execução que você iniciou** e execute novamente o mesmo comando trocando `--host 127.0.0.1` pelo IP privado da interface de rede do seu servidor. Reserve esse endereço no roteador para que ele não mude. Restrinja a porta à TV ou à rede privada pelo firewall; não faça redirecionamento da porta para a internet.

O endpoint fornecido não possui autenticação. Quem alcançar a porta poderá consultar o feed, embora as credenciais não sejam retornadas. Para manter o serviço ligado após fechar o terminal, configure seu próprio gerenciador de serviços, contêiner ou sessão persistente usando esses mesmos arquivos e argumentos. Este repositório não fornece uma instalação automática no seu servidor.

## 4. Apontar o APK para o serviço

Copie `config.example.properties` para `config.properties`, se ainda não houver uma cópia sua. Depois configure:

```properties
channel_feed_url=http://IP_DO_SERVIDOR:3330/channel
show_channel_metrics=false
```

Substitua `IP_DO_SERVIDOR` pelo IP privado real. A URL é compilada no APK; não coloque senhas ou tokens nela. O host e a porta precisam ser alcançáveis pela TV.

### HTTP dentro da rede local

O Android bloqueia HTTP por padrão neste fork. Além de configurar a URL, edite `app/res/xml/network_security_config.xml` para permitir **somente o host exato** do seu servidor:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">IP_DO_SERVIDOR</domain>
    </domain-config>
</network-security-config>
```

Troque o marcador pelo mesmo host da URL, sem `http://`, porta ou caminho. Isso é uma alteração local para sua instalação; não publique o arquivo com seu endereço particular. Não habilite tráfego em texto claro de forma global. A exceção permite o tráfego, mas não o cifra; use-a apenas em rede privada confiável.

### HTTPS

Se você já tem um proxy HTTPS privado com certificado válido e confiável pela TV, use uma URL como `https://feed.exemplo.com/channel` e mantenha `cleartextTrafficPermitted="false"`. O hostname é um exemplo e deve ser substituído por um domínio seu. O backend Python serve HTTP, portanto o TLS precisa ser terminado por outro componente. Não desative a validação do certificado para contornar erros.

Em ambos os casos, compile seguindo o [README](../README.md#compilar-sua-versão):

```sh
./build.sh
```

Instale o APK com a mesma chave de assinatura de seus builds anteriores. Se estiver migrando do APK público para um build seu, provavelmente haverá diferença de assinatura: **restaure Original e confira a volta antes de desinstalar a versão anterior**. A conta, os tokens e os caches continuam no servidor.

## 5. Conferir na TV

Verifique os estados do feed nas configurações, a chegada das miniaturas e as abas Vídeos/Lives. Navegue por vários grupos de três cards, abra um vídeo e volte com Início. Se a coleta não funcionar, confira `/health`, autorização, acesso da TV ao servidor e a exceção HTTP ou o certificado HTTPS.

As métricas opcionais são do canal público ViktorKav e ficam separadas das inscrições. Ativar `show_channel_metrics=true` não muda essas métricas para sua conta. Para outro canal, veja o [mapa das customizações](customizacoes-viktorkav.md).

## O que o feed faz e o que guarda

- Consulta uploads recentes das inscrições autorizadas, com catálogo de até 512 candidatos e até cinco publicações recentes por inscrição. Não carrega todo o histórico do YouTube.
- Atualiza o catálogo de canais periodicamente e ajusta a cadência das consultas ao volume de inscrições. Não é uma atualização instantânea nem uma garantia de permanecer dentro de qualquer cota disponível.
- Mantém métricas públicas e recomendações em caches separados, com indicação de dados antigos quando uma atualização falha. O servidor pode conservar o último resultado válido; revogar a conta no Google não apaga automaticamente arquivos já gravados.
- Filtra Shorts por duração e, quando necessário, consulta a rota pública `/shorts/` sem enviar OAuth. Essa verificação depende de comportamento do site e pode mudar; casos indeterminados são omitidos, inclusive alguns vídeos comuns.
- Não reproduz o algoritmo da home, o histórico de assistidos ou todas as recomendações da interface oficial do YouTube.

O arquivo OAuth, o catálogo de canais, os caches e seus backups são privados. A TV também guarda um cache do feed e miniaturas. Para encerrar o uso, remova o acesso do aplicativo na conta Google, pare somente o serviço que você configurou e trate os arquivos privados conforme sua política de backup. Antes de desinstalar o launcher, restaure a home Original.
