# Verificação da versão 1.7.0-gta.2

Base original: branch `viktorkav` de `viktorkav/free-my-tv`, commit `4b95e7451628e6655a14d3d5596083adb257c14b`. Continuação da GTA VI `1.7.0-gta.1` com o mesmo pacote `io.freemytv` e certificado. Versão nova: `versionCode 11`.

## Executado na nuvem

- JDK 17 e Android SDK Build-Tools / plataforma 34.
- `python3 -m unittest discover -s tests -p 'test_*.py' -q`: 43 testes aprovados.
- `bash tests/run-java-tests.sh`: 1.114 verificações aprovadas. Além das coberturas anteriores: seleção vazia/default/ordem/limite dos apps, referências de canais/importação, URLs/redirects oficiais, XML inválido/DTD, filtros de trailers, deduplicação de categorias, retenção de cache parcial e rejeição de respostas de gerações antigas.
- `bash build.sh`: APK compilado. Avisos de APIs obsoletas não impedem o build.
- `apksigner verify --verbose --print-certs`: assinatura v3 válida, certificado SHA-256 `f555ccc7fe855e1bcf8c7bc96eecc523838dc856aaa5a0b3388a6ef89aad7308`, igual à gta.1.
- `zipalign -c 4`: alinhamento aprovado.
- 11 capturas oficiais e logo continuam locais, com MIT e créditos nos assets. Nenhum canal pessoal ou chave privada incluído nos arquivos públicos/APK; build público sem chave TMDB nem endereço de feed pessoal.
- `HomeMode.java`, `Adb.java`, `ModeJob.java`, `Watchdog.java`, `HomeReceiver.java`, `Screensaver.java` e `LICENSE` iguais à base original.
- Revisão independente de navegação, edição, importação e segurança do feed: corrigidos reinício indevido da página com atualização de conteúdo e contraste dos campos de texto na skin clara.
- `git diff --check`: aprovado. Estado e desenho registrados em `docs/superpowers/`.

## Evidência do aparelho e limitações

O usuário relatou instalação bem-sucedida da gta.1 na TCL/43S5K Android 14, galeria funcionando e retorno à tela Original. Isso não valida a gta.2.

O agente não acessou a rede local da TV nem instalou no aparelho. Não houve execução da nova interface em emulador/TV. O proxy desta nuvem bloqueia YouTube; disponibilidade real dos feeds públicos, resolução dos handles e abertura dos vídeos precisam ser verificadas na rede da TV. Testes de parser usam dados sintéticos, sem favoritos pessoais.

A aparência e fluidez do salto/neon, navegação do editor, persistência após atualizar, fontes públicas/trailers e intervalo do descanso devem ser conferidos pelo controle conforme [INSTRUCOES-INSTALACAO.md](INSTRUCOES-INSTALACAO.md). A versão continua experimental até essa verificação. Em falhas de rede, a interface mostra o estado e mantém dados válidos já salvos; não inventa recomendações.

## Atualização e retorno

A gta.2 atualiza a gta.1 com `adb install -r`, sem desinstalação nem limpeza de dados. Guardar o APK anterior não equivale a guardar preferências; `allowBackup=false` continua preservado. Não force downgrade para a gta.1.

Para voltar à home de fábrica sem apagar configurações: Free My TV → **Início → Original**, aguardar e confirmar com Home. Antes de qualquer futura desinstalação, confirmar esse retorno. Nenhum mecanismo de recuperação foi alterado.
