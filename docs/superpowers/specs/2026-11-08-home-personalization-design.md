# Home personalizada, YouTube e trailers — desenho

Estado: aprovado pelo usuário. Extensão aprovada no mesmo pedido: aplicativo selecionado se eleva suavemente e mantém borda neon da paleta GTA VI.

## Objetivo e contexto
O usuário instalou e testou pessoalmente a aparência GTA VI e a galeria na TCL/43S5K com Google TV / Android 14. Pediu galeria mais rápida, edição dos aplicativos da home, trailers em vídeo e vídeos recentes de seis canais indicados. Escolheu abrir os trailers no YouTube ao apertar OK e aceitou atualização do APK por cima, sem desinstalar ou apagar preferências.

A atualização será única, versão 1.7.0-gta.2 (versionCode 11), pacote io.freemytv e o mesmo certificado da versão GTA VI instalada: SHA-256 f555ccc7fe855e1bcf8c7bc96eecc523838dc856aaa5a0b3388a6ef89aad7308. Publicação no fork Alencar1992/free-my-tv, branches de código e downloads já existentes. A versão .1 permanece disponível. Instalação e teste físico são feitos pelo usuário; nenhum comando para modificar a TV é executado nesta sessão.

## Abordagens consideradas e escolhas
- Galeria: 15 segundos na aparência GTA VI, mantendo a transição de 3 segundos. As outras aparências mantêm o intervalo atual de 30 segundos. Escolha explícita do usuário para GTA VI.
- Atualização: o usuário escolheu atualizar por cima usando APK com a mesma assinatura. Enviar arquivos ao GitHub não altera a TV automaticamente. A implementação não inclui instalação automática nem uma nova permissão de instalador.
- Trailers: o usuário escolheu abrir o app oficial do YouTube com OK; player embutido foi descartado. Não baixar/extrair streams, não reproduzir automaticamente e não prometer acesso ao catálogo ou ao histórico privado de cada serviço.
- YouTube: canais explicitamente escolhidos pelo usuário, em vez de inferir histórico. A API pública não fornece o histórico de assistidos. Proposta do implementador: feeds públicos de uploads consultados pela TV, sem servidor separado, chave de API ou OAuth; o serviço opcional antigo continua compatível.

## Experiência proposta
### Aplicativos
Adicionar em Configurações um item Personalizar home, abrindo uma tela com navegação pelas setas, OK e Voltar, cores/fontes da aparência atual e alvos de foco legíveis. Aplicativos da home lista os atalhos escolhidos e permite adicionar, remover o atalho e subir/descer sua posição. Usar até sete apps para preservar o tamanho/navegação do dock existente. Não remover ou desabilitar aplicativos instalados. O próprio Free My TV não entra na lista selecionável; Configurações e Todos os Apps permanecem acessíveis.

A lista selecionável vem dos apps de TV instalados e habilitados. Os apps fora do dock continuam em Todos os Apps, incluindo apps antes fixos que o usuário retirar. Preferências são aplicadas ao voltar à home. Disponibilizar Restaurar atalhos padrão, que altera somente a seleção do dock. Seleção vazia deve ser segura, com acesso a Configurações e Todos os Apps. Não limpar outras preferências para restaurar o dock. Apps que desapareceram não provocam erro e não ganham dados inventados.

### Vídeos favoritos
Na nova tela, Canais do YouTube aceita links /channel/UC... ou @handles, com edição/remoção pelo controle, limite de 12 canais e sem duplicatas. Os seis links enviados pelo usuário não ficam em fontes, fixtures, APK público, README ou histórico Git. Entregar um comando local opcional de importação via ADB para abrir a tela de revisão da lista; somente após confirmação visível no controle os canais são gravados. A entrada de importação usa SettingsActivity já exportada, valida formato/tamanho e nunca troca a home ou limpa outras preferências. A tela de edição é interna, não exportada. Importação/substituição mostra o que será salvo e pode ser cancelada.

Exibir miniaturas, título, nome do canal e data de vídeos recentes. Shorts e lives podem aparecer se publicados no feed: esta implementação não promete classificação por duração quando o feed não fornece esse dado. Publicações de texto da Comunidade e histórico personalizado não fazem parte deste escopo. OK abre o vídeo no YouTube; Voltar/Home segue o fluxo normal do app de vídeo e do launcher.

Resolver @handles somente nos hosts oficiais HTTPS do YouTube e validar o ID UC obtido dos metadados do canal. Consultar o feed oficial de uploads do canal. Validar IDs de canal e vídeo e construir URLs conhecidas de vídeo/miniatura; não aceitar endpoints arbitrários ou credenciais em URLs.

### Trailers de streaming
Nova aba Trailers com vídeos promocionais de canais oficiais dos serviços. Oferecer predefinições apenas quando a origem oficial for verificável e permitir editar/adicionar a fonte pública associada a um app de streaming instalado. Filtrar títulos de uploads por trailer/teaser/prévia, deixando claro que não é o catálogo completo nem a lista Continuar assistindo da conta. Um app sem origem disponível aparece sem conteúdo inventado. Não solicitar credenciais Netflix/Prime/Google ou extrair dados privados de outros apps.

Fontes predefinidas têm origem oficial pesquisada/documentada e são propostas para Netflix Brasil, Prime Video Brasil, Disney+ Brasil e Max Brasil. Fontes que o usuário cadastrar manualmente são rotuladas Fonte personalizada; validar um canal não comprova afiliação oficial e a interface não deve afirmar isso. Para um serviço sem predefinição, é possível associar um canal público manualmente.

A tela Personalizar home permite ligar/desligar os trailers e escolher as fontes. Os apps selecionados como fontes precisam estar instalados e habilitados. A abertura dos trailers usa o mesmo app do YouTube e os mesmos links de vídeo válidos dos favoritos; não abrir resultados de busca como se fossem um trailer identificado.

### Home e coexistência com o feed antigo
Usar abas Recentes, YouTube e Trailers quando a descoberta pública estiver habilitada. Preservar a navegação por grupos de três cards, ordem estável das páginas e deduplicação de vídeo. Um vídeo presente em favoritos e trailers pode aparecer nas respectivas abas, mas não se repete no conjunto Recentes. Preservar o comportamento existente Vídeos/Lives quando apenas o feed legado estiver em uso. A fonte ativa é mutuamente exclusiva e explícita: ao escolher Canais e trailers, somente favoritos e trailers públicos preenchem essas três abas; recomendações do servidor ficam temporariamente fora da home. Ao voltar a Feed do servidor, o modo legado e as abas Vídeos/Lives retornam. A configuração antiga não é alterada nem apagada. O padrão da nova preferência preserva Feed do servidor se Config.CHANNEL_FEED_URL já estiver preenchida; sem servidor configurado, o padrão é Canais e trailers. A importação de favoritos mostra revisão e a opção explícita de usar o conteúdo importado na home. Métricas do canal original continuam independentes e ocultas por padrão.

## Dados, rede e falhas
Preferências privadas no armazenamento do app: seleção/ordem do dock, canais favoritos, fontes de trailers, ativação da descoberta pública. Não embutir a lista pessoal no APK distribuído. Cache privado por canal, limites de 12 fontes de favoritos e 8 de trailers, até cinco vídeos recentes por fonte, limite global de 100 itens e miniaturas restritas à janela visível/seguinte do código atual. Intervalo de consulta de 15 minutos, com Atualizar agora, tempos limite e nenhum laço de tentativas imediato.

Carregar cache e consultar rede fora da thread principal. Salvar arquivos atomicamente. Se a rede falhar, manter o último cache válido e indicar que está desatualizado. Se um canal falhar, os outros continuam disponíveis. Se nenhum dado existir, exibir um estado vazio claro com acesso à configuração/atualização, sem dados simulados ou vídeos inventados. Alterar canais durante uma consulta invalida resultados antigos; remover um canal deixa de expor seus vídeos sem esperar outra consulta.

O proxy deste ambiente recusou as conexões ao YouTube com HTTP CONNECT 403. Isso limita a validação ao vivo aqui; não prova bloqueio na rede residencial da TV. Não contornar o proxy, TLS ou restrições de origem. Testar parser com fixtures e falhas reais simuladas; validar chegada dos feeds na TV após a atualização e documentar essa limitação. Se o YouTube também negar feeds na TV, informar o erro e usar cache; a integração oficial por servidor já documentada continua uma alternativa separada, que exige configuração própria. Não prometer atualizações instantâneas ou disponibilidade universal dos feeds públicos.

Feeds e miniaturas utilizam HTTPS com certificados válidos. Manter bloqueio global de HTTP e a política de exceção de host exato do projeto. Não acrescentar acesso a contas, histórico, contatos, microfone, instalação de pacotes ou privilégios de sistema para essas funções. Nenhum endpoint público pessoal ou OAuth será hospedado nesta tarefa.

## Preservação e entrega
Manter Android 12+, Java sem Gradle, textos português/inglês, MIT, créditos Simba/simbasjungles e ViktorKav e atribuições das imagens/fontes. Não alterar HomeMode.PROTECTED, proteção de Wi-Fi, listas de pacotes, watchdog ou recuperação. Atualização preserva a skin e as preferências existentes, acrescentando novas chaves privadas; não usar SharedPreferences.clear(). A alteração para 15s afeta apenas GTA VI.

Publicar o código, APK atualizado, certificado, hashes e instruções de atualização com adb install -r. Não pedir desinstalação nem limpar dados. Verificar a assinatura antes de publicar e compará-la com o certificado .1. Dar instruções de retornar a Original e manter o APK .1 como referência; não prometer downgrade direto de versionCode maior. A chave privada permanece fora do repositório e não deve ser exportada novamente sem necessidade.

## Critérios de aceitação
- Galeria GTA VI muda em 15s e outras aparências mantêm 30s.
- Adicionar/remover/reordenar atalhos persiste e não desinstala apps; dock vazio e pacotes ausentes são seguros.
- Canais/handles válidos, duplicatas, XML malformado/DTD, limites, redirecionamento não autorizado, falha parcial/total, cache antigo e mudança de configuração durante consulta têm comportamento definido e testes pertinentes.
- Cards recentes/YouTube/trailers preservam identidade, categorias e navegação; OK abre o vídeo correto no YouTube.
- Importação pessoal exige revisão no controle e não aparece em arquivos públicos.
- Testes Java/Python, build.sh, apksigner, zipalign, verificações de privacidade/arquivos protegidos, revisão de código e download público conferido.
- Teste físico a repetir pelo usuário: atalhos, foco, retorno dos vídeos, cache/atualização, galeria de 15s e retorno a Original. O relato anterior de funcionamento da galeria .1 não prova as novas telas ou os feeds.

## Decisões deixadas ao implementador
Organização exata das classes e controles nativos, mensagens bilingues, layout que acomode o novo item sem sobreposição e tratamento de versões menores de XML/JSON. Presets só após verificar a origem; nenhuma conta ou dado do usuário deve ser presumido. Prefira a menor mudança que satisfaça essas condições e registre desvios.

## Foco neon aprovado
Na skin GTA VI, os aplicativos focados na home e em Todos os Apps ganham elevação/escala suave com a animação de mola existente e uma borda neon rosa/lilás/pêssego. Manter a borda enquanto focado, sem piscar continuamente e sem alocações pesadas em onDraw. Preservar contraste e comportamento das outras skins.
