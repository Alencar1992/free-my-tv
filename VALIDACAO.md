# Verificação da versão 1.7.0-gta.3

Base imediata: gta.2, commit `d4661740bb1fec40d759833a1477282cc5596e62`. Base original ViktorKav: `4b95e7451628e6655a14d3d5596083adb257c14b`. Pacote `io.freemytv`, Android12+, código de versão12.

## Executado

- Reprodução antes da correção: feed com cabeçalho sem prefixo UC era rejeitado com `Unexpected feed channel`.
- `bash tests/run-java-tests.sh`: 1.117 verificações aprovadas. Os três novos casos verificam compatibilidade do cabeçalho, rejeição de outro canal e manutenção do ID completo nos vídeos.
- `python3 -m unittest discover -s tests -p 'test_*.py' -q`:43 testes aprovados.
- `bash build.sh`: build com JDK17/Android34 aprovado; avisos de APIs obsoletas não impedem a compilação.
- `apksigner verify --verbose --print-certs` e `zipalign -c 4`: aprovados. Certificado SHA-256 `f555ccc7fe855e1bcf8c7bc96eecc523838dc856aaa5a0b3388a6ef89aad7308`, mesmo da gta.1/gta.2.
- Nenhum favorito pessoal, keystore ou configuração de feed pessoal nos arquivos públicos/APK. MIT, créditos e11 imagens/logo preservados.
- `HomeMode.java`, `Adb.java`, `ModeJob.java`, `Watchdog.java`, `HomeReceiver.java`, `Screensaver.java` e `LICENSE` preservados em relação à base original.

## Aparelho

O usuário confirmou instalação/galeria/retorno a Original da gta.1, atualização e personalização da gta.2, e reprodução normal no YouTube da TV. A gta.2 apresentou falha nos feeds públicos.

A gta.3 corrige uma incompatibilidade reproduzida no parser, mas não foi instalada/testada na TCL pelo agente. Não houve emulador nem acesso à rede local da TV. O proxy da nuvem bloqueia YouTube. O teste real da busca e resolução dos canais precisa ser repetido no aparelho; a correção local não comprova ausência de outras falhas de rede ou disponibilidade.

[Diagnóstico e limites da conclusão](docs/diagnostico-fontes.md). [Atualizar sem desinstalar e voltar a Original](INSTRUCOES-INSTALACAO.md).
