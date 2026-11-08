# Free My TV — aparência GTA VI

Personalização para Android TV / Google TV, baseada na branch **viktorkav** de [ViktorKav/free-my-tv](https://github.com/viktorkav/free-my-tv), que preserva o Free My TV original de **Simba / simbasjungles e contribuidores**. A licença MIT e os créditos originais permanecem incluídos.

**[Baixar APK GTA VI](https://github.com/Alencar1992/free-my-tv/releases/tag/v1.7.0-gta.2)** · **[Instalação, assinatura e retorno a Original](INSTRUCOES-INSTALACAO.md)** · [Fontes das imagens](app/assets/wallpapers/SOURCES.md)

A terceira aparência **GTA VI** acrescenta fundo fixo oficial de Vice City, roxo/lilás/rosa com foco pêssego, títulos fortes e rótulos condensados. O descanso dessa aparência tem **11 capturas oficiais locais**, com Leonida, carros, Jason, Lucia e outros personagens, logo e relógio; troca a cada 15 segundos com transição de 3 segundos. As duas skins anteriores continuam disponíveis. Na skin GTA VI, selecionar um app levanta o cartão com animação de mola e borda neon rosa/lilás/pêssego. Em **Personalizar home**, escolha até 7 apps, sua ordem, até 12 canais públicos do YouTube e fontes de trailers dos apps instalados. OK em um vídeo abre o YouTube; não há reprodução automática nem consulta ao histórico. Canais e preferências ficam privados na TV. O feed de servidor continua opcional e pode ser selecionado separadamente.

Versão experimental `1.7.0-gta.2` (`versionCode 11`), Android 12+. Mantém o certificado da gta.1 e permite atualização por cima com `adb install -r`, preservando os dados. O usuário confirmou instalação e galeria da gta.1 na TCL/43S5K com Android 14; os novos recursos da gta.2 ainda precisam de teste físico. Veja [VALIDACAO.md](VALIDACAO.md).

A personalização não é afiliada à Rockstar Games / Take-Two. Imagens e marcas do jogo pertencem aos respectivos titulares e não são relicenciadas como MIT. Fontes usadas têm suas licenças preservadas; a fonte proprietária da Rockstar não é distribuída.

---

# Free My TV — versão ViktorKav

Minha adaptação do **[Free My TV, criado por Simba / simbasjungles](https://github.com/simbasjungles/free-my-tv)**: uma tela inicial para Google TV e Android TV, com duas skins, atalhos de aplicativos e um feed opcional das suas inscrições do YouTube. O projeto original é distribuído sob a licença MIT; este fork preserva seus créditos e sua base.

**[Tutorial e material de apoio](https://viktorkav.com.br/tutoriais/free-my-tv.html)** · **[Baixar o APK deste fork](https://github.com/viktorkav/free-my-tv/releases/tag/v1.7.0-vkav.1)** · [Personalizar o código](docs/customizacoes-viktorkav.md) · [Configurar seu feed](docs/feed-pessoal.md)

## O que mudou

- **Duas skins selecionáveis:** ViktorKav, com grafite e foco ciano; e Apple Liquid Glass, uma interpretação visual com vidro e modos claro/noturno. A segunda não é um produto da Apple.
- **Três vídeos grandes por grupo**, navegáveis pelas setas do controle, com canais diferentes no grupo e sem repetir o mesmo vídeo durante a sessão. O catálogo tem um fim.
- **Feed pessoal opcional:** vídeos recentes dos canais da conta que você autorizar, com filtragem de Shorts. A coleta roda em um servidor seu, separado da TV.
- **Atalhos pessoais:** YouTube, Twitch, Apple TV, Prime Video, Jellyfin, Stremio e Globoplay, quando disponíveis no aparelho. Outros aplicativos continuam em Todos os Apps.
- **Tela de descanso e configurações** acompanham a skin; o descanso usa as miniaturas disponíveis no feed e exibe relógio e clima.

O launcher substitui a tela inicial. Ele não remove anúncios de dentro dos aplicativos, não libera conteúdo pago e não substitui assinaturas de serviços.

## O que vem no APK público

A release `v1.7.0-vkav.1` inclui as skins e a interface, **sem conta Google, credenciais, inscrições ou endereço de servidor do Victor**. O feed começa desativado e as métricas do canal ficam ocultas. Você pode usar os atalhos e trocar a aparência sem montar um servidor.

Os cards de inscrições precisam de [configuração adicional](docs/feed-pessoal.md) e de um APK compilado com o endereço do seu serviço. Não há um campo na TV para digitar esse endereço nesta versão.

O idioma acompanha o aparelho, com textos em português e inglês. O clima usa coordenadas genéricas de Nova York por padrão; a cidade é configurável ao compilar. A localização não é detectada automaticamente.

## Compatibilidade e limites

Este fork exige **Android 12 ou mais recente** (`minSdk 31`). Uma TV com Google TV ou Android TV, ou um aparelho externo com esse sistema conectado por HDMI, é o caminho compatível. Ainda é necessário que o aparelho permita ADB e a troca da tela inicial.

As adaptações que deram origem ao fork foram usadas em uma TCL com Android 12. Isso não comprova funcionamento em todas as TVs, e não representa um teste físico da nova release pública. O caminho de shaders do Android 13+ também pode se comportar de forma diferente do fallback do Android 12.

Este APK não é instalável em webOS ou Tizen. Consulte o [projeto original](https://github.com/simbasjungles/free-my-tv) para outras plataformas e versões; os requisitos dele podem ser diferentes dos deste fork.

O consumo de memória e a resposta do controle variam por aparelho e configuração. O teste apresentado no tutorial mostrou menor uso de memória em uma rodada, sem demonstrar ganho de velocidade. Não há promessa de acelerar sua TV.

## Instalar o APK pronto

Você precisa de um computador, do controle da TV e dos dois aparelhos na mesma rede.

1. Baixe o APK na [release deste fork](https://github.com/viktorkav/free-my-tv/releases/tag/v1.7.0-vkav.1) e o [Android SDK Platform Tools oficial](https://developer.android.com/tools/releases/platform-tools) para seu computador. Extraia o Platform Tools e coloque `FreeMyTV.apk` nessa pasta.
2. Na TV, abra Configurações → Sistema → Sobre e pressione sete vezes o item de compilação do Android TV para liberar as opções de desenvolvedor. Volte e ative Depuração USB. Os nomes dos menus variam por fabricante.
3. Nos detalhes de Rede e Internet, anote o IP da TV. Abra o terminal na pasta `platform-tools`.
4. Conecte. **Troque `IP_DA_TV` pelo endereço do seu aparelho. Execute uma linha por vez: após conectar, aceite a autorização na TV e só prossiga quando `devices` mostrar `device`.**

macOS/Linux:

```sh
./adb connect IP_DA_TV:5555
./adb devices
```

Windows/PowerShell:

```powershell
.\adb.exe connect IP_DA_TV:5555
.\adb.exe devices
```

Autorize a conexão pelo controle quando a TV pedir. Em `devices`, o aparelho deve aparecer como `device`; `unauthorized` indica autorização pendente. Alguns modelos exigem pareamento sem fio ou habilitação específica da porta 5555. Consulte a [documentação do ADB](https://developer.android.com/tools/adb) e o procedimento do seu aparelho quando esse caminho não funcionar.

5. Com o estado `device` confirmado, instale o APK:

```sh
./adb -s IP_DA_TV:5555 install -r FreeMyTV.apk
```

No PowerShell, use `.\adb.exe` no lugar de `./adb`. Aguarde `Success`. Abra Free My TV na televisão. Antes da troca da home, desconecte o computador:

```sh
./adb disconnect IP_DA_TV:5555
```

No PowerShell, use `.\adb.exe` no lugar de `./adb`.

6. No próprio aplicativo, pelo controle, escolha **Início / Home Screen → Free My TV**. Pode aparecer uma segunda autorização de depuração: agora é o aplicativo conectando à própria TV.
7. Teste Início, abertura e retorno dos apps, volume, entradas e atalhos do controle. Confira também a opção **Original**, antes de continuar personalizando.

### Particularidade de algumas TCL

Se a troca não iniciar por bloqueio de execução em segundo plano, reconecte o computador:

```sh
./adb connect IP_DA_TV:5555
./adb devices
```

Aceite a autorização na TV se for solicitada e confirme o estado `device`. Só depois aplique a permissão:

```sh
./adb -s IP_DA_TV:5555 shell appops set io.freemytv AUTO_START allow
```

Esse foi um ajuste necessário na TCL usada no projeto; não é um comando universal para outras marcas. Depois, desconecte o ADB do computador e repita a troca pelo aplicativo.

### Se já existe outro Free My TV instalado

Este fork conserva o pacote Android `io.freemytv`, mas a release pública usa **uma assinatura própria**. Um APK do projeto original ou um build pessoal pode ter outra assinatura e produzir `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

Nesse caso, abra a versão instalada, selecione **Original** e confira que a home de fábrica voltou. Só então desinstale o aplicativo pelas configurações da TV e instale este APK. A desinstalação apaga as preferências locais. Não tente forçar uma atualização com assinatura incompatível.

## Voltar para a tela original

Abra Free My TV → Início / Home Screen → **Original**, usando o controle. O app tenta restaurar o launcher, o screensaver e o estado dos pacotes que registrou antes da mudança. Confira os recursos do aparelho depois da volta.

**Volte para Original antes de desinstalar.** Desinstalar diretamente pode deixar os aplicativos desativados sem restaurá-los. Se isso já ocorreu, a recuperação depende dos pacotes e do launcher específicos do aparelho; consulte `HomeMode.java` e o registro anterior antes de habilitar pacotes ou definir uma atividade de início.

O projeto mantém um watchdog para retornar à home de fábrica após falhas repetidas. Ele depende da autorização ADB e da execução em segundo plano e não substitui um backup integral da TV. Pacotes que salvaram redes Wi-Fi recebem proteção específica; preserve essas verificações ao modificar o código.

## Compilar sua versão

O Android é escrito em Java e compilado sem Gradle. Você precisa de JDK 17 ou mais recente e Android SDK com build-tools e plataforma 34 ou superiores. Consulte as [ferramentas oficiais do Android](https://developer.android.com/tools).

```sh
git clone --branch viktorkav https://github.com/viktorkav/free-my-tv.git
cd free-my-tv
cp config.example.properties config.properties
```

Edite `config.properties` antes do build. Para preparar o SDK já instalado:

```sh
sdkmanager "build-tools;34.0.0" "platforms;android-34"
./build.sh
```

Informe `JAVA_HOME` e `ANDROID_HOME` se as ferramentas não forem encontradas. Em Windows, use um ambiente Bash compatível, como WSL, para compilar, e o Platform Tools do Windows para se conectar à TV.

O sucesso termina com `Built FreeMyTV.apk`. `build.sh` cria `freemytv.keystore` no primeiro build: guarde essa chave em local privado, pois as próximas atualizações precisam da mesma assinatura. Seu build não terá a assinatura da release pública; siga a seção de migração acima quando necessário.

| Configuração | Uso neste fork |
|---|---|
| `latitude`, `longitude`, `city` | Coordenadas e nome da sua cidade para clima e ciclo dia/noite. |
| `language` | `auto`, `pt` ou `en`. |
| `temperature` | `C`, `F` ou `auto`. |
| `channel_feed_url` | URL do seu serviço opcional; vazio por padrão. Veja o [guia do feed](docs/feed-pessoal.md), inclusive a configuração HTTP/HTTPS. |
| `show_channel_metrics` | Exibe métricas públicas do canal ViktorKav; `false` por padrão. Não são métricas da conta autorizada. |
| `disable_assistant` | `false` por padrão. Ativar também desliga o Assistente e pode afetar o botão de microfone do controle. |

Os campos TMDB e criptomoedas são herdados do upstream. As recomendações desta adaptação usam o feed do YouTube; não é preciso obter uma chave TMDB ou configurar Binance.

## Desenvolvimento e privacidade

- [Guia das customizações](docs/customizacoes-viktorkav.md): mapa do código, skins, navegação e um pedido inicial para usar com um agente de IA.
- [Guia do feed pessoal](docs/feed-pessoal.md): API, OAuth somente leitura, serviço na rede local e compilação do cliente.
- [Instruções para agentes](CLAUDE.md): regras para alterar, testar e instalar o projeto.

O clima consulta Open-Meteo com as coordenadas configuradas. Com o feed ativo, a TV consulta seu servidor e baixa miniaturas do YouTube. O servidor consulta a API do YouTube e verifica o formato de alguns vídeos pela rota pública de Shorts. Chaves de API e credenciais OAuth ficam no servidor; o APK contém a URL que você configurar e dados de clima, portanto trate esses campos como informação extraível do aplicativo.

O feed revela seus vídeos e canais de interesse a quem puder acessá-lo na rede. Ele deve ficar restrito à sua rede privada; não há autenticação no endpoint HTTP fornecido. Sem feed configurado, não há acesso à conta ou às inscrições de ninguém.

## Créditos e licença

O trabalho original é de **[Simba / simbasjungles e contribuidores do Free My TV](https://github.com/simbasjungles/free-my-tv)**, que relata ter criado o projeto com Claude Code. As adaptações de visual e feed foram feitas por ViktorKav com ajuda de Codex. Este fork parte do commit upstream `73409804e5085e720dccea9ab95078a515cae098`; a numeração de suas releases é independente.

A [licença MIT original](LICENSE) permanece incluída. Inter, IBM Plex Sans e JetBrains Mono têm licenças OFL em `app/assets/fonts/`; Material Symbols usa Apache 2.0. As fontes de logos estão em [app/assets/logos/SOURCES.md](app/assets/logos/SOURCES.md). Clima por [Open-Meteo](https://open-meteo.com/). Marcas, nomes e logos dos aplicativos pertencem aos respectivos titulares; não há afiliação com Google, Apple, TCL, Twitch ou serviços de streaming.
