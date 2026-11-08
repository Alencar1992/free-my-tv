# Xbox na home GTA VI

A conexão opcional utiliza [OpenXBL](https://xbl.io/), serviço independente da Microsoft. O usuário cria sua própria conta e chave. O app faz somente consultas GET ao perfil, histórico e dados do jogo selecionado; não desbloqueia conquistas, envia mensagens ou altera dados do Xbox.

## Configurar

1. Atualize o APK por cima usando `install -r`.
2. Abra **Personalizar home → 🎮 Xbox · jogos e estatísticas**.
3. Digite sua API Key pessoal no campo mascarado e escolha **Conectar**. A senha Microsoft não é solicitada pela skin. O campo não é salvo/restaurado como texto, não é preenchido com a chave antiga e é limpo após o envio.
4. Busque no histórico, navegue nas páginas e aperte OK em um jogo para exibi-lo na home. Cada linha tem nome e imagem do jogo, quando a CDN a fornecer.
5. Para voltar pelo controle, use Voltar. Para reabrir na home, suba até as abas, aperte Cima para focar o painel Xbox e OK. Baixo retorna ao item anterior.

Não envie a chave por chat, GitHub, arquivos públicos ou comandos ADB. Se preferir um teclado de celular, use o controle remoto do Google TV com a TV pareada. Uma falha de armazenamento protegido não provoca gravação em texto aberto; a conexão informa que não pôde proteger a chave.

## Dados e limites

- 🏆 Conquistas desbloqueadas/total; 🏅 Gamerscore, quando retornado.
- ⏱️ Tempo derivado de **MinutesPlayed**. Campo ausente aparece indisponível; zero informado continua zero.
- 📊 Percentual das conquistas, explicitamente diferente da campanha.
- 🎯 Tiros na cabeça, 🎒 corpos saqueados e ♠️ vitórias no pôquer são exemplos de métricas específicas; não aparecem com números fictícios em jogos que não as informam. Outras estatísticas retornadas recebem rótulos e emojis.
- Histórico retornado pelo serviço, até1.000 jogos; não é inventário completo de licenças compradas. Detalhes de alguns títulos, especialmente formatos antigos, podem ficar indisponíveis; a seleção e os dados de catálogo permanecem utilizáveis.
- Sem dados futuros presumidos de GTA VI. Escolha-o quando surgir no histórico após jogar, respeitando o acesso/privacidade do Xbox e a disponibilidade do provedor.

Atualização automática15min com a interface ativa; atualização manual30s por alvo. Limite interno100 consultas/hora, páginas/dados limitados e respeito ao429. O serviço externo pode aplicar limites próprios. Falhas preservam dados válidos e indicam cache; não há polling independente em segundo plano. Navegar de um jogo a outro não descarta detalhes recentes dos últimos20 jogos na mesma sessão.

## Privacidade

Chave criptografada AES/GCM com chave local do **Android Keystore** e arquivo interno privado. A chave nunca entra no APK/fontes/logs ou URLs. Dados de perfil/histórico/cache ficam no armazenamento interno privado, com backup do app desabilitado como na base original. A troca de conta ou desconexão invalida consultas anteriores, apaga os caches e impede respostas antigas de reaparecerem.

Consultas autenticadas só seguem para `https://xbl.io/api/v2` e não acompanham redirects. Imagens são consultadas separadamente nas CDNs Xbox/Microsoft permitidas, sem qualquer cabeçalho de autenticação. O app não tem servidor próprio nem distribui uma chave compartilhada.

## Verificação

Usuário confirmou leitura de perfil/histórico/conquistas/tempo no PC. Testes locais verificaram modelos, rate limit e o controlador real contra provedor HTTPS simulado. Isso não comprova Keystore, teclado, fluidez/capas e autenticação no dispositivo real. Compare os valores na TV com o Xbox e teste selecionar outro jogo, voltar ao anterior, reiniciar o app e desconectar.
