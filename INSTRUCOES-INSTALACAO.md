# Instalar e voltar — Free My TV GTA VI

Versão `1.7.0-gta.5`, pacote `io.freemytv`, Android 12+. Personalização do fork ViktorKav, baseado no Free My TV de Simba/simbasjungles e contribuidores, preservando a licença MIT.

## Downloads

- [Release com APK, checksums e certificado](https://github.com/Alencar1992/free-my-tv/releases/tag/v1.7.0-gta.5).
- [Baixar APK diretamente](https://github.com/Alencar1992/free-my-tv/raw/refs/heads/downloads/FreeMyTV-GTA-VI-1.7.0-gta.5.apk).
- [Pacote completo](https://github.com/Alencar1992/free-my-tv/raw/refs/heads/downloads/FreeMyTV-GTA-VI-Pacote.zip).
- [Código desta personalização — branch gta-vi](https://github.com/Alencar1992/free-my-tv/tree/gta-vi).
- [Platform Tools oficial para Windows](https://developer.android.com/tools/releases/platform-tools).

Baixe o APK pelos links acima ou pelo pacote completo. `SHA256SUMS.txt` e `ASSINATURA.txt` estão no pacote e na [branch downloads](https://github.com/Alencar1992/free-my-tv/tree/downloads). Os arquivos Source code da release contêm fontes; não são o aplicativo instalável. Compare `Get-FileHash .\FreeMyTV-GTA-VI-1.7.0-gta.5.apk -Algorithm SHA256` com `SHA256SUMS.txt` antes de usar o APK.

Esta atualização passou por testes de código e compilação; **os novos recursos ainda não foram testados fisicamente na TCL/43S5K com Google TV / Android 14**. O usuário confirmou a instalação da gta.1, a galeria e o retorno a Original, e depois o uso da personalização e dos feeds públicos após a gta.3. O agente não acessou nem modificou a TV.

## Novidades da gta.5 — painel Xbox

Após atualizar por cima, abra **Free My TV → Personalizar home → 🎮 Xbox · jogos e estatísticas**. Informe sua **API Key pessoal do OpenXBL** no campo mascarado da TV e aperte **Conectar**. Você pode usar o teclado do controle remoto do Google TV no celular, se disponível. A chave é validada e guardada criptografada pelo Android Keystore. Ela não deve ser enviada nesta conversa, em screenshots do dashboard, por comandos ADB ou publicada no GitHub. O APK não contém uma chave compartilhada.

Busque o nome do jogo, navegue nas páginas e aperte **OK** na linha com sua imagem para selecioná-lo na home. A tela mostra os dados disponíveis: 🏆 conquistas, ⏱️ tempo, 📊 percentual de conquistas, 🏅 Gamerscore e estatísticas específicas com emojis, quando retornadas. Percentual de conquistas não é progresso da campanha.

Na home GTA VI, o painel fica ao lado do contador. Suba até as abas Recentes/YouTube/Trailers; aperte **Cima** novamente para focar o painel Xbox e **OK** para abrir a busca. **Baixo** volta ao item anterior. Selecionar outro jogo altera os dados e a imagem do painel; o fundo e o contador GTA VI continuam como antes. Depois de20/11, o contador some e o painel Xbox continua.

A lista é o histórico retornado pelo Xbox, com limite de1.000 jogos; títulos comprados e nunca jogados podem não aparecer. Alguns títulos antigos ou estatísticas podem não ser fornecidos pelo serviço; nesses casos aparecem valores indisponíveis, sem zeros inventados. GTA VI aparecerá para seleção quando for retornado no seu histórico; não há promessa antecipada de percentual de campanha.

A atualização automática ocorre a cada15 minutos com a home ativa. **Atualizar agora** permite consulta manual, com intervalo mínimo de30 segundos por jogo e limites de uso do provedor. Dados recentes de até20 jogos ficam em memória durante a navegação; a seleção e o último detalhe são guardados privadamente. Em erro temporário, dados válidos salvos ficam indicados como cache. **Desconectar e remover chave** remove a conexão e os dados privados do painel.

Teste no aparelho: conectar, conferir o perfil, buscar um jogo já jogado, verificar tempo/conquistas com o Xbox, selecionar outro e voltar ao anterior, fechar/reabrir e conferir seleção/capa; depois usar as abas e apps normalmente. O teste de API no PC foi bem-sucedido, mas a integração da gta.5, o teclado, as imagens e o Android Keystore precisam dessa validação na TV. [Detalhes da conexão](docs/xbox.md).

## Novidades da gta.4 — fotos da home e contador

Na aparência **GTA VI**, a home alterna as mesmas11 fotos locais do descanso a cada **15 segundos**, com uma transição breve. Funciona sem internet ou feed. Ao abrir outro aplicativo ou Todos os Apps, a troca de fotos é pausada. Ao voltar, a foto atual fica por mais15 segundos; o foco e os canais continuam preservados. Uma imagem com falha é pulada; se nenhuma puder ser lida, permanece o fundo grafite.

Ao lado do nome **GTA VI**, o contador usa a paleta rosa/lilás/pêssego e a fonte já licenciada da skin, com o formato **DD/HH/MM/SS**:

- Antes de19/11/2026 às00:00 de Brasília: dias, horas, minutos e segundos restantes.
- Durante19/11/2026: **00/00/00/00** e **HOJE É O GRANDE DIA!**.
- A partir de20/11/2026 às00:00 de Brasília: o bloco inteiro fica oculto automaticamente; a galeria continua.

A data é o alvo solicitado pelo usuário. O cálculo não acessa um servidor e usa o relógio da TV; mantenha a data e a hora corretas. O fuso do alvo é fixo **America/Sao_Paulo**, mesmo que a TV use outro fuso. Ao reabrir a home, o estado é recalculado; se o relógio antes estava indevidamente no futuro e foi corrigido para trás, uma verificação por minuto também recupera o bloco oculto com a home aberta.

Teste com o controle após atualizar: mantenha GTA VI selecionada, observe os segundos e aguarde pelo menos **um minuto** para conferir quatro mudanças de foto. Navegue nas abas e nos apps durante a troca, abra um app e volte à home, depois confira Todos os Apps. As duas skins anteriores devem continuar como antes. As viradas de19 e20 foram testadas com horários simulados no código, sem alterar a data da TV; o teste visual da gta.4 ainda está pendente.

## Correção de fontes indisponíveis na gta.3

A gta.2 podia rejeitar feeds válidos porque exigia o prefixo `UC` no identificador do cabeçalho. A gta.3 aceita o cabeçalho com ou sem esse prefixo, sempre correspondente ao canal pedido, e continua exigindo o identificador completo nos vídeos. Não precisa de servidor próprio ou chave de API.

Após atualizar por cima, abra **Personalizar home → Fonte → Canais e trailers**, confira os canais e selecione **Atualizar canais e trailers agora**. Aguarde a busca terminar; dependendo da conexão e quantidade de fontes, pode levar até cerca de dois minutos. A correção foi reproduzida em testes; disponibilidade real de cada canal ainda depende da resposta do YouTube na rede da TV.

Se continuar indisponível, depois de tentar atualizar consulte somente os registros do app:

```powershell
.\adb.exe -s $TV logcat -d -s FreeMyTV:I '*:S'
```

Envie as linhas com `public-video-fetch`. Elas registram a etapa, tipo de falha e eventual HTTP, sem publicar seus canais ou conteúdo do feed. O comando não altera a TV. A presença de um erro isolado não indica falha de todos os canais; fontes com dados válidos continuam disponíveis.

## Atualizar a GTA VI já instalada, preservando dados

Se você já instalou a `1.7.0-gta.1`, `1.7.0-gta.2`, `1.7.0-gta.3` ou `1.7.0-gta.4` deste repositório, esta versão usa **a mesma chave**, com certificado SHA-256:

`f555ccc7fe855e1bcf8c7bc96eecc523838dc856aaa5a0b3388a6ef89aad7308`

Guarde o APK anterior e as anotações das preferências. Confira o APK novo com o checksum e o certificado abaixo. Com a TV conectada e `$TV` definido no PowerShell, na pasta que contém `adb.exe` e o APK:

```powershell
.\adb.exe -s $TV install -r '.\FreeMyTV-GTA-VI-1.7.0-gta.5.apk'
```

Espere `Success`. **Não desinstale, não limpe dados e não use `-d`.** O pacote e a chave são os mesmos, e a atualização conserva as preferências. Caso apareça incompatibilidade de assinatura, pare e confira os certificados; não remova o app como tentativa de correção.

Abra Free My TV → **Personalizar home**:

- **Aplicativos da home:** adicione até 7, aperte OK em um deles para mover à esquerda/direita ou remover o atalho. Remover um atalho mantém o app instalado e em Todos os Apps. “Restaurar atalhos padrão” altera somente essa seleção.
- **Canais do YouTube:** adicione até 12 links de canais ou `@nome`. O histórico e as publicações de texto não são acessados. A importação privada, se recebida separadamente, abre uma revisão na TV; Cancelar preserva seus canais atuais.
- **Trailers de streaming:** ative e configure até 8 fontes vinculadas aos apps instalados. Netflix Brasil, Prime Video Brasil, Disney+ Brasil e Max Brasil têm predefinições quando os apps correspondentes estão na home. Links adicionados manualmente aparecem como “Fonte personalizada”. São os trailers/teasers encontrados entre os uploads recentes públicos, não o catálogo personalizado ou completo do serviço.
- **Fonte:** “Canais e trailers” mostra as abas Recentes, YouTube e Trailers. “Feed do servidor” preserva o modo anterior, quando configurado; são alternativas.
- **Atualizar canais e trailers agora:** busca novos vídeos. A atualização automática tem intervalo de 15 minutos. Até 5 vídeos por fonte; em falhas, dados válidos já salvos continuam disponíveis. Sem dados e sem acesso ao YouTube, a home informa indisponibilidade.

Aperte OK em um cartão para abrir o vídeo no YouTube da TV. Não há player embutido nem autoplay. Os canais pessoais não estão nos downloads públicos.

Na aparência **GTA VI**, o app selecionado sobe com uma animação curta e fica com borda neon rosa, lilás e pêssego. Isso vale para o dock e Todos os Apps. O descanso troca de imagem a cada **15 segundos**, com a transição suave de 3 segundos. As outras skins mantêm seu comportamento.

Para testar pelo controle: reorganize um atalho, feche/reabra e confira a ordem; remova todos temporariamente e confira Configurações e Todos os Apps; restaure os padrões; escolha canais/fontes, confira as abas, abra um vídeo e volte. Observe quatro trocas no descanso por pelo menos um minuto. Confira foco, legibilidade e fluidez; este teste físico ainda está pendente.

**Voltar ao Google TV:** Free My TV → **Início → Original**, aguarde e pressione Home para conferir. Não exige desinstalar. Mudar a home e instalar são decisões separadas. Não tente voltar à gta.1 usando downgrade forçado; prefira Original e uma correção assinada com versão maior para manter os dados.

Os passos completos abaixo atendem também a uma primeira instalação ou a um app de outra origem.

## 1. Abrir a pasta certa no Windows

No PowerShell:

```powershell
Set-Location -LiteralPath 'C:\CAMINHO\PARA\platform-tools'
(Get-Location).Path
Get-Item -LiteralPath '.\adb.exe'
.\adb.exe version
```

Substitua o caminho de exemplo pela pasta `platform-tools` que contém `adb.exe` no seu computador. Você pode copiar o caminho pela barra de endereços do Explorador de Arquivos. A pasta local não pode ser validada a partir da sessão na nuvem. Copie o APK baixado para essa pasta. Não sobrescreva um APK antigo: guarde-o em uma pasta de backup.

## 2. Conectar e fazer inventário

Computador Windows e TV devem estar na mesma rede. Se a depuração já estiver habilitada, use a conexão existente. Caso não esteja, a habilitação nas opções do desenvolvedor e a autorização na TV são ações manuais, a fazer somente quando decidir prosseguir. Os menus e o modo de pareamento variam entre modelos TCL.

Defina o endereço real anotado na TV; `5555` é apenas o caso tradicional. Se o aparelho usa Depuração sem fio, use o IP/porta indicados por ele, com `adb pair` quando necessário; não force portas nem altere a rede.

```powershell
$TV = 'IP_DA_TV:5555'
.\adb.exe connect $TV
.\adb.exe devices -l
.\adb.exe -s $TV get-state
```

Autorize pelo controle se for solicitado. Só continue se `get-state` responder `device`. Não prossiga com `unauthorized`, `offline` ou múltiplos aparelhos sem identificar a TV. Os comandos abaixo consultam o estado; não instalam nem trocam a home:

```powershell
New-Item -ItemType Directory -Force -Path '.\backup-tv' | Out-Null
.\adb.exe -s $TV shell getprop ro.product.model
.\adb.exe -s $TV shell getprop ro.build.version.release
.\adb.exe -s $TV shell getprop ro.build.version.sdk
.\adb.exe -s $TV shell dumpsys package io.freemytv | Set-Content '.\backup-tv\pacote-instalado.txt'
.\adb.exe -s $TV shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | Set-Content '.\backup-tv\home-atual.txt'
.\adb.exe -s $TV shell cmd role get-role-holders --user 0 android.app.role.HOME | Set-Content '.\backup-tv\home-role.txt'
.\adb.exe -s $TV shell pm list packages -d | Set-Content '.\backup-tv\pacotes-desativados.txt'
.\adb.exe -s $TV shell settings get secure screensaver_components | Set-Content '.\backup-tv\descanso-atual.txt'
.\adb.exe -s $TV shell settings get secure screensaver_default_component | Set-Content '.\backup-tv\descanso-padrao.txt'
.\adb.exe -s $TV shell pm path io.freemytv
```

Guarde também a aparência selecionada e as preferências visíveis do Free My TV, fotografando/anotando as telas. Registre cidade, feed e arquivos privados de builds anteriores, se os tiver. A marca/modelo, sozinhos, não comprovam a atividade de home correta; em algumas TCL Android 14 o papel HOME e o launcher que responde ao botão podem divergir.

## 3. Guardar o APK instalado e comparar assinaturas

Se `pm path io.freemytv` não retornar pacote instalado, não há assinatura anterior desse pacote para comparar. Preserve o inventário mesmo assim. Se retornar `package:/.../base.apk`, copie o caminho real, sem o prefixo `package:`:

```powershell
.\adb.exe -s $TV pull '/CAMINHO_REAL/base.apk' '.\backup-tv\base.apk'
```

Se aparecerem outros APKs (splits), copie **cada um deles** para `backup-tv`, preservando seus nomes. Um APK de referência compilado na nuvem não substitui esse backup extraído da TV.

Para verificar o certificado, use o `apksigner.bat` oficial dos **Android SDK Build-Tools**. Ele não vem no Platform Tools. Instale os Build-Tools pelo Android Studio/SDK Manager oficial se ainda não os tiver. O verificador também precisa de Java: confira `java -version` e disponibilize um JDK 17+ pelo PATH ou JAVA_HOME. Este é um caminho comum; ajuste para a versão efetivamente instalada:

```powershell
$ApkSigner = "$env:LOCALAPPDATA\Android\Sdk\build-tools\34.0.0\apksigner.bat"
Test-Path -LiteralPath $ApkSigner
& $ApkSigner verify --print-certs '.\backup-tv\base.apk'
& $ApkSigner verify --print-certs '.\FreeMyTV-GTA-VI-1.7.0-gta.5.apk'
```

Compare `Signer #1 certificate SHA-256 digest` das duas saídas e confira também `ASSINATURA.txt` da release. Sem conseguir verificar, pare aqui. Não descubra uma incompatibilidade tentando instalar por cima. Se houver rotação/histórico de assinaturas, a compatibilidade precisa ser analisada; não presuma que um hash diferente seja uma atualização comum.

### Se a assinatura for compatível

Uma atualização normal pode preservar os dados privados do mesmo pacote. Guarde o backup e confira o código de versão: esta release usa `versionCode 14`. Se o app instalado tiver código maior, não force downgrade.

### Se a assinatura for diferente

Este APK usa uma chave própria, diferente da release ViktorKav e de outros builds pessoais, salvo comprovação de compatibilidade. O app mantém `allowBackup=false`, não oferece exportação/importação das preferências e normalmente não permite `run-as` em builds de distribuição. Portanto:

1. Não tente forçar a atualização nem use desinstalação como correção automática.
2. Preserve os APKs instalados, o inventário e o registro manual das preferências. Se já houver um backup privado acessível por um meio legítimo, guarde-o também; copiar o APK sozinho não copia preferências.
3. Com o controle, abra a versão instalada → **Início / Home Screen → Original**. Isso já modifica a TV: faça apenas após sua decisão de prosseguir.
4. Pressione Home e confirme a tela de fábrica. Confira volume, entradas, rede e os pacotes restaurados. Se houver erro ou a home não voltar, pare sem desinstalar.
5. Só após aceitar que a desinstalação apaga os dados locais e definir como reconstruir suas preferências, desinstale pelas configurações da TV e instale o novo APK.
6. Reaplique as preferências registradas. Não copie cegamente `home-state.json` antigo para o app novo: ele registra o estado de recuperação da instalação anterior. A autorização ADB interna também pode precisar ser refeita.

Se preservar as configurações integralmente for obrigatório e não houver exportação/backup comprovado, **não faça essa migração ainda**. Nenhuma promessa de backup integral foi feita.

## 4. Instalar — somente depois da conferência e da confirmação

Este comando modifica o aparelho. Não foi executado pelo agente. Use-o apenas depois de resolver os passos anteriores e confirmar a instalação:

```powershell
.\adb.exe -s $TV install -r '.\FreeMyTV-GTA-VI-1.7.0-gta.5.apk'
```

Espere `Success`. Se aparecer `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, pare: a assinatura precisa ser resolvida conforme a seção anterior. Não desinstale às cegas. Abra **Free My TV** pela lista de aplicativos da televisão; a instalação sozinha não é a aprovação para trocar a home.

No controle, escolha **Aparência → GTA VI**, depois **Tela de Descanso → Ver agora**. Confira a prévia antes de ativar o launcher. A galeria tem 11 fotos locais, mudança a cada 15 segundos, transição de 3 segundos, logo e relógio. Setas, OK e Voltar fecham a prévia. O tempo de inatividade para abrir o descanso automático continua sendo definido pelo sistema da TV.

Se decidir ativar a home, desconecte o ADB do computador antes, conforme o projeto original:

```powershell
.\adb.exe disconnect $TV
```

No app, pelo controle, escolha **Início → Free My TV** e autorize a conexão ADB interna se solicitada. Algumas TCL precisam da permissão `AUTO_START`; ela não deve ser aplicada preventivamente. Se a troca não iniciar por esse bloqueio, reconecte, confirme `device` e só então avalie a permissão específica documentada no README original.

## 5. Teste com o controle e retorno

- Troque entre GTA VI, ViktorKav e Apple Liquid Glass e confira que a seleção fica salva.
- Confira leitura a distância, foco, setas, OK e Voltar. Abra um app e retorne com Home.
- Teste volume, entradas HDMI/TV aberta, atalhos e microfone.
- Abra a prévia do descanso, aguarde pelo menos 90 segundos e confira três fotos, legendas, logo e transições; depois verifique um ciclo completo (cerca de 4 minutos).
- Confirme que o descanso funciona sem depender do servidor do feed. Não altere a rede da TV para esse teste sem uma decisão separada.
- Volte a **Início → Original**, pressione Home e confirme o launcher de fábrica, o descanso e os recursos do controle.

**Para voltar:** Free My TV → Início / Home Screen → **Original**. Aguarde terminar e confira a volta da home pelo controle. O watchdog é uma proteção adicional; depende da autorização ADB e da execução em segundo plano e não substitui essa conferência.

**Sempre retorne a Original antes de desinstalar.** Se quiser reinstalar a versão antiga após uma migração entre assinaturas, primeiro confirme Original na versão nova, só então desinstale e reinstale os APKs guardados. Para um único APK, use `adb install`; para um pacote dividido, use `adb install-multiple` com todos os splits correspondentes. Reaplique as preferências registradas. Não use comandos genéricos para remover APKs de sistema, limpar dados ou habilitar pacotes desconhecidos.

## Chave das próximas atualizações

Esta release foi assinada com uma chave privada nova. As próximas atualizações desta personalização devem usar a mesma chave. Ela não está no repositório público nem nos assets da release. Se receber um backup privado separadamente, guarde-o fora do repositório; não publique o keystore ou sua senha. Sem a chave, um novo build pessoal terá outra assinatura e precisará do procedimento de migração descrito acima.
