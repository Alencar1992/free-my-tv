# Verificação da versão 1.7.0-gta.1

Base: branch `viktorkav` de `viktorkav/free-my-tv`, commit `4b95e7451628e6655a14d3d5596083adb257c14b`.

## Executado na nuvem

- JDK 17 e Android SDK Build-Tools / plataforma 34, com downloads oficiais verificados por checksum.
- `python3 -m unittest discover -s tests -p 'test_*.py' -q`: 43 testes aprovados.
- `bash tests/run-java-tests.sh`: 1.054 verificações aprovadas. Cobrem parser, navegação, contraste, ID persistido da skin, amostragem/recorte, catálogo offline, ciclo sem repetição imediata, imagens com falha e falha total.
- `./build.sh`: APK compilado com sucesso. O aviso de API obsoleta de `Spoken.java` já existia na base; não impede a compilação.
- `apksigner verify --verbose --print-certs`: assinatura v3 válida. O certificado público e o hash do APK acompanham a release.
- `zipalign -c 4`: alinhamento aprovado.
- Conferência do APK: 11 fotos e logo incluídos; bytes iguais aos checksums do manifesto de origem. MIT e créditos também incluídos nos assets.
- Comparação com a base: `HomeMode.java`, `Adb.java`, `ModeJob.java`, `Watchdog.java`, `HomeReceiver.java`, `Screensaver.java` e `LICENSE` preservados.
- Revisão independente do código: seleção da skin, galerias, fundo, renderização, cancelamento por geração e liberação de bitmaps. Um caminho pessoal do Windows foi retirado do guia e do histórico que será publicado.
- `git diff --check` e inspeção dos arquivos versionados: sem chave privada, APK ou configuração pessoal no código publicado.

## Pendente no aparelho

Ainda não houve conexão com a TCL/43S5K nem execução física/emulador da interface nesta sessão. Portanto, testes de código e compilação não comprovam aparência, fluidez, resposta do controle ou restauração na TV.

É necessário conferir a versão e o certificado do app instalado, guardar os APKs e o inventário e definir preservação das preferências antes de instalar. `allowBackup=false` impede prometer backup automático. Nenhuma instalação ou mudança da home foi executada.

O teste proposto pelo controle está em [INSTRUCOES-INSTALACAO.md](INSTRUCOES-INSTALACAO.md): aparência, galeria, abrir/voltar dos apps, Home, volume, entradas, microfone e retorno comprovado a Original. A release é experimental até essa verificação.
