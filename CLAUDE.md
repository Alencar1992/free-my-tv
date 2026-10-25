# Free My TV — instruções para agentes

Este é o fork ViktorKav de `simbasjungles/free-my-tv`. Java sem Gradle; interface desenhada em Canvas; Android 12+. O código original de troca de home e recuperação foi preservado. O README descreve o produto público e os requisitos atuais.

## Leia conforme a tarefa

- **Instalar, atualizar ou voltar à home original:** [README.md](README.md), incluindo assinatura e particularidades do aparelho.
- **Alterar skins, navegação, cards ou descanso:** [docs/customizacoes-viktorkav.md](docs/customizacoes-viktorkav.md).
- **Configurar ou modificar coleta, OAuth, cache ou transporte do feed:** [docs/feed-pessoal.md](docs/feed-pessoal.md).

## Fluxo de alteração

1. Confira o estado do Git e a versão instalada antes de mudar arquivos. Preserve trabalho e configurações locais do usuário.
2. Edite fontes em `app/src/`, `app/res/`, `app/assets/` ou `tools/`. `out/gen/io/freemytv/Config.java` é gerado por `build.sh`; a fonte de configuração é `config.properties`.
3. Mantenha os textos bilíngues com `Ui.tr("pt", "en")`. Na interface, mantenha I/O e processamento pesado fora da thread principal e evite alocações durante `onDraw`.
4. Execute os testes pertinentes e `./build.sh`. Para uma alteração que cruza app e servidor, execute ambos os grupos abaixo. A compilação deve terminar com `Built FreeMyTV.apk`.
5. Relate o que foi verificado. Testes unitários e build não comprovam funcionamento físico na TV; identifique separadamente uma verificação feita no aparelho.

```sh
python3 -m unittest discover -s tests -p 'test_*.py' -q
bash tests/run-java-tests.sh
./build.sh
```

## TV e recuperação

- Faça inventário de marca, sistema, versão, launcher e recursos do controle antes de adaptar a troca. A marca sozinha não determina compatibilidade.
- Explique as alterações e deixe a pessoa confirmar **Free My TV** ou **Original** no controle. Instalação/configuração não autoriza alterar contas, imagem, som ou rede do aparelho.
- Preserve `HomeMode.PROTECTED`, a proteção de pacotes que salvaram Wi-Fi, o registro anterior e o watchdog. A operação permitida para a limpeza é desativar pacotes selecionados; nunca remover APKs de sistema ou limpar seus dados.
- Uma atualização precisa da mesma assinatura. Se houver conflito, restaurar Original e conferir a volta precede qualquer desinstalação.
- Depois de uma instalação autorizada, confira Início, abrir/voltar dos apps, entradas, volume e atalhos. Faça a verificação de Original pelo controle com a pessoa. A recuperação depende de ADB autorizado e execução em segundo plano.

## Configuração e publicação

- O build público parte de `config.example.properties`, com feed vazio e métricas ocultas. Cidade e idioma usam os defaults públicos. Credenciais Google pertencem ao serviço opcional, nunca ao APK.
- `config.properties`, keystores, `.env`, credenciais OAuth, caches e dumps pessoais ficam fora do Git. Trate também `out/` e APKs pessoais como dados potencialmente sensíveis; configurações compiladas podem ser extraídas.
- HTTP requer uma exceção para o host exato em `app/res/xml/network_security_config.xml`; mantenha o bloqueio geral. HTTPS usa certificado válido. O serviço deve ficar na rede privada, porque o endpoint não autentica clientes.
- O canal das métricas é público e fixo no código. A identidade autorizada por OAuth fornece as inscrições, não muda esse canal.
- Preserve a licença MIT original, atribuições de assets e licenças das fontes. Mantenha créditos ao upstream ao publicar modificações.
