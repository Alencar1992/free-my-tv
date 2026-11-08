# Instalar e voltar — Free My TV GTA VI

Versão `1.7.0-gta.1`, pacote `io.freemytv`, Android 12+. Personalização do fork ViktorKav, baseado no Free My TV de Simba/simbasjungles e contribuidores, preservando a licença MIT.

## Downloads

- [Release com APK, checksums e certificado](https://github.com/Alencar1992/free-my-tv/releases/tag/v1.7.0-gta.1).
- [Baixar APK diretamente](https://github.com/Alencar1992/free-my-tv/raw/refs/heads/downloads/FreeMyTV-GTA-VI-1.7.0-gta.1.apk).
- [Pacote completo](https://github.com/Alencar1992/free-my-tv/raw/refs/heads/downloads/FreeMyTV-GTA-VI-Pacote.zip).
- [Código desta personalização — branch gta-vi](https://github.com/Alencar1992/free-my-tv/tree/gta-vi).
- [Platform Tools oficial para Windows](https://developer.android.com/tools/releases/platform-tools).

Baixe o APK pelos links acima ou pelo pacote completo. `SHA256SUMS.txt` e `ASSINATURA.txt` estão no pacote e na [branch downloads](https://github.com/Alencar1992/free-my-tv/tree/downloads). Os arquivos Source code da release contêm fontes; não são o aplicativo instalável. Compare `Get-FileHash .\FreeMyTV-GTA-VI-1.7.0-gta.1.apk -Algorithm SHA256` com `SHA256SUMS.txt` antes de usar o APK.

Esta versão passou por testes de código e compilação; **ainda não foi testada fisicamente na TCL/43S5K com Google TV / Android 14**. A instalação não foi executada pelo agente. Verificar versão, assinatura e configurações vem antes de qualquer alteração no aparelho. Instalar e trocar a home exigem sua confirmação separada.

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
& $ApkSigner verify --print-certs '.\FreeMyTV-GTA-VI-1.7.0-gta.1.apk'
```

Compare `Signer #1 certificate SHA-256 digest` das duas saídas e confira também `ASSINATURA.txt` da release. Sem conseguir verificar, pare aqui. Não descubra uma incompatibilidade tentando instalar por cima. Se houver rotação/histórico de assinaturas, a compatibilidade precisa ser analisada; não presuma que um hash diferente seja uma atualização comum.

### Se a assinatura for compatível

Uma atualização normal pode preservar os dados privados do mesmo pacote. Guarde o backup e confira o código de versão: esta release usa `versionCode 10`. Se o app instalado tiver código maior, não force downgrade.

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
.\adb.exe -s $TV install -r '.\FreeMyTV-GTA-VI-1.7.0-gta.1.apk'
```

Espere `Success`. Se aparecer `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, pare: a assinatura precisa ser resolvida conforme a seção anterior. Não desinstale às cegas. Abra **Free My TV** pela lista de aplicativos da televisão; a instalação sozinha não é a aprovação para trocar a home.

No controle, escolha **Aparência → GTA VI**, depois **Tela de Descanso → Ver agora**. Confira a prévia antes de ativar o launcher. A galeria tem 11 fotos locais, mudança a cada 30 segundos, transição de 3 segundos, logo e relógio. Setas, OK e Voltar fecham a prévia. O tempo de inatividade para abrir o descanso automático continua sendo definido pelo sistema da TV.

Se decidir ativar a home, desconecte o ADB do computador antes, conforme o projeto original:

```powershell
.\adb.exe disconnect $TV
```

No app, pelo controle, escolha **Início → Free My TV** e autorize a conexão ADB interna se solicitada. Algumas TCL precisam da permissão `AUTO_START`; ela não deve ser aplicada preventivamente. Se a troca não iniciar por esse bloqueio, reconecte, confirme `device` e só então avalie a permissão específica documentada no README original.

## 5. Teste com o controle e retorno

- Troque entre GTA VI, ViktorKav e Apple Liquid Glass e confira que a seleção fica salva.
- Confira leitura a distância, foco, setas, OK e Voltar. Abra um app e retorne com Home.
- Teste volume, entradas HDMI/TV aberta, atalhos e microfone.
- Abra a prévia do descanso, aguarde pelo menos 90 segundos e confira três fotos, legendas, logo e transições; depois verifique um ciclo completo (cerca de 6 minutos).
- Confirme que o descanso funciona sem depender do servidor do feed. Não altere a rede da TV para esse teste sem uma decisão separada.
- Volte a **Início → Original**, pressione Home e confirme o launcher de fábrica, o descanso e os recursos do controle.

**Para voltar:** Free My TV → Início / Home Screen → **Original**. Aguarde terminar e confira a volta da home pelo controle. O watchdog é uma proteção adicional; depende da autorização ADB e da execução em segundo plano e não substitui essa conferência.

**Sempre retorne a Original antes de desinstalar.** Se quiser reinstalar a versão antiga após uma migração entre assinaturas, primeiro confirme Original na versão nova, só então desinstale e reinstale os APKs guardados. Para um único APK, use `adb install`; para um pacote dividido, use `adb install-multiple` com todos os splits correspondentes. Reaplique as preferências registradas. Não use comandos genéricos para remover APKs de sistema, limpar dados ou habilitar pacotes desconhecidos.

## Chave das próximas atualizações

Esta release foi assinada com uma chave privada nova. As próximas atualizações desta personalização devem usar a mesma chave. Ela não está no repositório público nem nos assets da release. Se receber um backup privado separadamente, guarde-o fora do repositório; não publique o keystore ou sua senha. Sem a chave, um novo build pessoal terá outra assinatura e precisará do procedimento de migração descrito acima.
