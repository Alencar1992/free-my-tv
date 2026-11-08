# Verificação da versão1.7.0-gta.5

Base imediata gta.4: `d7bd72137f340e00280850127af00f0227d3b374`. Base original ViktorKav: `4b95e7451628e6655a14d3d5596083adb257c14b`. Pacote `io.freemytv`, Android12+, código14.

## Executado

- `bash tests/run-java-tests.sh`:1.190 verificações de modelos Java aprovadas, além dos testes de política Xbox. Novos casos: respostas perfil/histórico/conquistas/estatísticas, pesquisa sem distinção de acentos, tempo desconhecido versus zero, percentual de conquistas separado de campanha, paginação/dedup e URLs de imagens permitidas.
- `bash tests/run-xbox-source-tests.sh`: política e controlador real contra HTTPS simulado aprovados. Verifica ausência de consultas antes de conectar, host/GET/sem redirects, perfil/histórico/paginação/estatísticas, IDs válidos,429/cache, troca/desconexão durante consulta, respostas antigas e navegação A→B→A preservando detalhes.
- `python3 -m unittest discover -s tests -p 'test_*.py' -q`:43 testes aprovados.
- `bash build.sh`: build JDK17/Android34 aprovado. Assinatura v3 e `zipalign -c 4` aprovados, certificado igual às versões anteriores: `f555ccc7fe855e1bcf8c7bc96eecc523838dc856aaa5a0b3388a6ef89aad7308`.
- Revisão independente: dados/credenciais/cache/gerações/CDN, fonte, UI, geometria do cabeçalho e navegação. Corrigidos com regressões RED→GREEN: tentativas automáticas após erro respeitam15min; retornar A→B→A preserva detalhe recente com cache limitado e limpo por conta. Nenhum achado crítico/importante pendente.
-11 imagens, logo, MIT e créditos preservados; nenhum segredo ou preferência pessoal embutido. Nenhuma nova permissão. XboxActivity não é exportada e não recebe chave por Intent.
- `HomeMode.java`, `Adb.java`, `ModeJob.java`, `Watchdog.java`, `HomeReceiver.java`, `Screensaver.java` e `LICENSE` iguais à base original.

## Evidência e limites no aparelho

O usuário mostrou feeds/contador anteriores funcionando e confirmou consultas OpenXBL no próprio PC com um jogo atual. Nenhuma chave pessoal foi fornecida ao agente ou usada na nuvem.

A nova integração Android ainda precisa de teste físico. Não houve instalação pelo agente, emulador, validação real do Android Keystore, teclado, capas ou navegação DPAD da gta.5. Os testes de controlador substituem o transporte por um provedor simulado e usam stubs Android/vault; não são uma validação do Keystore no dispositivo.

O histórico não garante todos os jogos comprados. Dados de títulos antigos/estatísticas ausentes permanecem indisponíveis; nenhuma porcentagem de campanha/GTA futuro é inventada. Detalhes em [Xbox](docs/xbox.md) e [instalação/retorno](INSTRUCOES-INSTALACAO.md).

Mesma chave/pacote: atualizar por cima com `install -r`, sem desinstalar ou limpar dados. Para voltar à home de fábrica: Free My TV → Início → Original; aguardar e conferir com Home. O contador e a galeria continuam locais e independentes do Xbox.
