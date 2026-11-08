# Customizar a versão ViktorKav

## Aparência GTA VI desta personalização

Em Configurações → Aparência → **GTA VI**, a home usa uma captura oficial fixa de Vice City, painéis roxo profundo, detalhes lilás/rosa e foco pêssego. Títulos usam Inter Bold e rótulos usam a família condensada do Android; não são as fontes proprietárias da Rockstar.

Na mesma aparência, **Tela de Descanso → Ver agora** abre uma galeria offline com 11 capturas oficiais: Vice City e outras regiões de Leonida, carros, Jason, Lucia, o casal e personagens como Cal Hampton e Boobie Ike. As fotos mudam a cada 30 segundos com transição de 3 segundos e o logo do jogo aparece no canto superior esquerdo. As imagens mantêm a proporção e as legendas acompanham português/inglês. A galeria não depende do feed do YouTube. O sistema da TV continua determinando quando o descanso automático começa; esse tempo não foi alterado.

`GameGallery.java` define o catálogo e salta imagens que falharam. `Wallpaper.java` carrega assets em background com limites de resolução; `ImageSizing.java` define a amostragem e o recorte do fundo da home. `DreamView.java` reutiliza o ciclo de preparação/transição. Atribuições, URLs e checksums estão em [app/assets/wallpapers/SOURCES.md](../app/assets/wallpapers/SOURCES.md).

As duas aparências anteriores e seus IDs persistidos permanecem disponíveis. O código de troca de launcher, watchdog e pacotes protegidos foi preservado. Veja [o guia desta versão](../INSTRUCOES-INSTALACAO.md) antes de instalar: a assinatura é própria, o backup Android está desativado e a TCL/43S5K ainda precisa de teste físico.

O [Free My TV original](https://github.com/simbasjungles/free-my-tv) fornece a base Android e a troca de launcher. Este fork acrescenta identidade visual, skins e um feed opcional. Para instalar primeiro, use o [README](../README.md); para acompanhar a experiência do vídeo, veja o [tutorial](https://viktorkav.com.br/tutoriais/free-my-tv.html).

## O que já está implementado

**ViktorKav** usa superfícies grafite durante todo o dia, foco ciano, IBM Plex Sans e JetBrains Mono. **Apple Liquid Glass** é uma interpretação com transparência, bordas arredondadas e paletas clara/noturna. A seleção é salva no aparelho e é compartilhada por home, configurações e tela de descanso.

No Android 12, o vidro usa um fallback de textura, transparência e processamento de imagem. O caminho de shaders AGSL fica disponível no Android 13+. Não presuma resultados visuais ou desempenho idênticos entre versões.

O feed exibe **três cards por grupo**. As setas esquerda/direita percorrem os grupos; a navegação preserva a ordem das páginas já visitadas e evita repetir um vídeo durante a sessão. Cada grupo procura canais distintos. As abas distinguem vídeos e lives. Quando o catálogo acaba, a interface informa o fim; ela não inventa novos resultados.

O dock inclui os aplicativos disponíveis dentre os atalhos escolhidos para o projeto. Isso não instala aplicativos nem concede acesso aos serviços. A gaveta Todos os Apps continua permitindo abrir os demais aplicativos compatíveis.

## Onde alterar

| Objetivo | Arquivos de entrada |
|---|---|
| Paletas, nomes e características das skins | `app/src/io/freemytv/Skin.java`, `TvTheme.java` |
| Persistência da skin e fontes | `SkinPreferences.java`, `Ui.java`, `app/assets/fonts/` |
| Home, foco, disposição dos cards e dock | `HomeView.java`, `VideoRail.java`, `CardGrid.java` |
| Apps e intenção de abertura | `HomeApps.java` |
| Configurações e textos de estado | `SettingsActivity.java` |
| Tela de descanso e vidro | `DreamView.java`, `GlassRaster.java`, `Backdrop.java`, `Scene.java` |
| Parser e atualização do feed na TV | `ChannelFeed.java`, `ChannelSource.java`, `Recs.java` |
| Coleta e cache no servidor | `tools/channel_feed.py`, `tools/subscription_feed.py` |
| Autorização da conta | `tools/authorize_subscriptions.py` |
| Build e configurações compiladas | `build.sh`, `config.example.properties` e sua cópia privada `config.properties` |

Os caminhos Java abreviados na tabela ficam em `app/src/io/freemytv/`. A troca de home, listas de pacotes protegidos, ADB interno e watchdog ficam em `HomeMode.java`, `Adb.java`, `ModeJob.java`, `HomeReceiver.java` e `Watchdog.java`. Uma mudança de cor ou layout não precisa alterar esses mecanismos.

## Um primeiro pedido para um agente de IA

Use uma pasta de trabalho sua com o código deste fork. Substitua os campos abaixo e comece por uma mudança pequena:

```text
Quero modificar este Free My TV para minha TV com [sistema e versão].
Hoje o que me incomoda é [problema concreto].
Minha primeira mudança será [uma única alteração].

Leia o README e o CLAUDE.md, confira a pasta de trabalho e explique quais
arquivos precisam mudar. A interface será vista a [distância] e usada
com as setas do controle. Preserve uma versão que funciona e explique
como voltar antes de instalar. Compile e mostre o resultado para revisão.
```

Exemplos úteis: aumentar o contraste do item selecionado, ampliar letras para leitura distante, reorganizar os atalhos ou criar uma nova paleta. Guarde referências e decisões no seu projeto. Ao testar, repita a mesma sequência: trocar skin, mover foco, abrir um app, voltar, entrar nas configurações e conferir o descanso.

Se quiser mudar a cidade, edite latitude, longitude e nome em `config.properties` e compile novamente. O endpoint do feed também é uma configuração de build nesta versão; o [guia do feed](feed-pessoal.md) explica os dois lados da conexão.

## O que exige trabalho adicional

Home Assistant, câmeras, agenda, controle da casa e novos serviços não são funções prontas deste fork. Cada integração exige definir origem dos dados, permissões, atualização, falhas e navegação pelo controle. Faça primeiro uma versão pequena com uma função verificável.

As métricas opcionais exibem dados públicos do canal ViktorKav. Para acompanhar outro canal, é necessário adaptar conjuntamente `CHANNEL_ID` em `tools/channel_feed.py` e `app/src/io/freemytv/ChannelFeed.java`, além dos rótulos e testes correspondentes. Autorizar outra conta no OAuth muda somente as inscrições do feed.

## Compilar e verificar

Siga os requisitos de build no [README](../README.md#compilar-sua-versão). Os testes existentes cobrem parser, seleção de vídeos, navegação, skins, vidro e serviço Python:

```sh
python3 -m unittest discover -s tests -p 'test_*.py' -q
bash tests/run-java-tests.sh
./build.sh
```

O script Java baixa uma dependência de teste da Maven Central na primeira execução. Ela não é incorporada ao app Android.

Uma compilação bem-sucedida não substitui a revisão no aparelho. Preserve a assinatura de seus builds; mudar a chave exige o procedimento de retorno a Original antes da reinstalação. Os números de memória mostrados no tutorial pertencem àquela medição e não servem como benchmark de outras personalizações.
