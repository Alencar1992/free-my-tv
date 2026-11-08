# Diagnóstico de fontes públicas — gta.3

## Evidência e hipótese

O usuário instalou a gta.2 por cima com `Success`. As fotos mostram a home GTA VI, atalhos personalizados/neon e depois as abas **Recentes / YouTube / Trailers**, com **Fontes indisponíveis**. Portanto, o modo público está selecionado e houve falha em pelo menos uma consulta. O usuário confirmou que o aplicativo YouTube da TV reproduz normalmente. Isso não comprova acesso ao endpoint de feeds pela skin, mas descarta uma falta geral de conexão no YouTube.

A versão gta.2 compara o `<yt:channelId>` do cabeçalho com o ID completo `UC` +22 caracteres. O cabeçalho dos feeds públicos também pode trazer somente os22 caracteres; os vídeos continuam trazendo o ID completo. A observação desse formato está documentada pelo autor de um cliente que o verificou diretamente: [youtube.py — modo RSS](https://github.com/aaron-he-zhu/aaron-marketing-skills/blob/main/scripts/connectors/youtube.py). A documentação oficial do [formato de notificações Atom](https://developers.google.com/youtube/data/guides/push_notifications) também situa o identificador completo no próprio vídeo; notificações e o feed público não têm necessariamente o mesmo cabeçalho.

## Reprodução e correção

Um teste com identificadores sintéticos reproduziu a rejeição da gta.2: `IllegalArgumentException: Unexpected feed channel`. O mesmo teste passa após aceitar somente duas representações equivalentes no cabeçalho: o ID completo esperado ou sua versão sem `UC`. IDs de outros canais são rejeitados e os IDs dos vídeos ainda precisam conter o prefixo e corresponder exatamente ao canal solicitado. URLs, HTTPS, redirects, limites e proteção contra DTD/entidades externas foram preservados.

Adicionado registro limitado de etapa (`metadata`, `feed`, `parse`), classe da exceção/causa e HTTP quando conhecido. Nunca registra referências de favoritos, corpo baixado ou mensagens arbitrárias de erros. Isso permite distinguir indisponibilidade de rede/YouTube, resolução de handles e leitura de XML se a falha continuar no aparelho.

## Limite da conclusão

É uma incompatibilidade de parser reproduzida e consistente com o sintoma. Não houve coleta da resposta HTTP/XML da própria TV nem acesso ADB ao aparelho pela nuvem. O proxy desta nuvem bloqueia o YouTube; o teste de ponta a ponta segue pendente na rede da TV. Não se afirma que todas as causas possíveis foram eliminadas.

O APK gta.3 mantém pacote/chave, sobe o código de versão para12 e pode atualizar gta.1/gta.2 com `install -r`, sem desinstalação ou limpeza de preferências. O agente não modificou a TV.
