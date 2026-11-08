# Aparência GTA VI para Free My TV

Estado: execução autorizada pelo pedido de adicionar o protetor de tela ao novo modo, após as escolhas de fundo fixo e captura oficial. Instalação na TV continua não autorizada.

## Intenção e escopo
Adicionar uma terceira aparência chamada GTA VI, selecionável em Configurações > Aparência pelo controle, para a TCL/43S5K informada pelo usuário com Google TV / Android 14. A cópia de trabalho fica na nuvem, separada da pasta Windows do usuário. A captura do site enviada pelo usuário orienta cores e estilo das letras; o fundo será uma captura oficial do jogo, não a ilustração de Jason e Lucia da referência. O usuário escolheu uma imagem fixa.

## Abordagens
Foram consideradas imagem fixa e galeria alternada. O usuário escolheu imagem fixa. Foram consideradas arte promocional da referência e captura oficial do jogo. O usuário escolheu captura oficial. A proposta original de substituir o fundo da skin ViktorKav foi ajustada para uma terceira aparência, preservando as duas existentes.

## Proposta visual
- ID persistente novo: gta-vi; rótulo: GTA VI.
- Superfícies em roxo profundo (#261D3D), detalhes lilás e rosa, foco pêssego (#FFD6A3), texto branco. Ajustar tons secundários para satisfazer os testes existentes de contraste.
- Títulos fortes com Inter Bold já licenciada no projeto; rótulos condensados com a família sans-serif-condensed do Android. São aproximações do estilo da referência, sem afirmar uso da fonte proprietária da Rockstar.
- Captura oficial obtida da página de GTA VI da Rockstar, com URL e checksum documentados. Seleção da cena fica a cargo do implementador; preferir paisagem com área livre para o conteúdo.
- Carregar a imagem local fora da thread principal; limitar decodificação à resolução da interface (1920 x 1080), recorte proporcional e véu escuro preparado fora de onDraw. Falha da imagem mantém uma superfície legível.
- Paleta e fontes consistentes em home, configurações e descanso. O fundo fixo é da home; somente a skin GTA VI ganha uma galeria local de descanso, independente do feed.

## Limites e preservação
Java e Canvas, sem Gradle; manter Android 12+ e os textos em português/inglês. Preservar os IDs das skins ViktorKav e Liquid Glass, navegação e configuração já salva. Preservar licença MIT, créditos Simba/simbasjungles e ViktorKav e licenças dos assets. A captura da Rockstar tem atribuição própria; não é relicenciada como MIT.
Não alterar HomeMode, PROTECTED, proteção Wi-Fi, watchdog, ADB, permissões, contas, som ou rede. Não instalar nem modificar a TV antes da confirmação específica do usuário.

## Verificação e entrega
Testar round-trip da nova skin, contraste de texto e foco, dimensões e recorte da imagem, falha de carregamento e preservação das skins existentes. Executar testes Java/Python e build.sh. Revisão física com controle permanece pendente: trocar aparência, navegar, abrir/voltar dos apps, Home, volume, entradas, microfone, descanso e retorno a Original.
Antes de instalar, extrair e conferir assinatura da versão instalada e compará-la com o novo APK. Com assinatura diferente, não desinstalar antes de comprovar Original e definir preservação das preferências; allowBackup=false impede prometer restauração automática. Não importar cegamente home-state.json entre instalações. Guardar o APK instalado para retorno; o build de referência da nuvem não é esse backup.

## Extensão solicitada: protetor de tela GTA VI
O usuário pediu fotos dinâmicas de Leonida, carros, NPCs, Jason e Lucia e o logo do jogo. Usar capturas reais oficiais de Vice City e outras regiões de Leonida, veículos e personagens; não gerar imagens. Alternar em ordem circular sem repetir imediatamente, a cada 30 segundos com transição de 3 segundos, reutilizando o ciclo existente. Fotos em tela cheia com proporção preservada e véu para legenda e relógio. Exibir logo oficial discreto na composição, carregado em background. A galeria vem no APK e funciona offline, sem depender do YouTube; falha de uma imagem avança à próxima, falha de todas mantém relógio/fundo legível. Descartar callbacks de gerações antigas e liberar bitmaps ao fechar. As skins anteriores continuam com seu descanso atual; as teclas de saída e tempos do sistema não mudam.
