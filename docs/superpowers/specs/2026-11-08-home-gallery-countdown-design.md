# Home GTA VI: galeria e contagem regressiva

Pedido do usuário: usar as mesmas11 fotos GTA VI já locais, alternando o fundo da home a cada15 segundos; ao lado do título GTA VI mostrar dias/horas/minutos/segundos, paleta e fonte da skin. Em19/11/2026 zerar e mostrar exatamente **HOJE É O GRANDE DIA!**. A partir de20/11, retirar o bloco automaticamente.

Decisão operacional anunciada: alvo em19/11/2026 às00:00 no fuso America/Sao_Paulo (Brasília); a data fornecida pelo usuário é o alvo do contador, sem depender de uma API de datas de lançamento. O relógio da TV deve estar correto. Usar as fontes licenciadas já presentes na skin, sem distribuir uma fonte comercial do logotipo.

Abordagem escolhida para a implementação: ciclo local que reutiliza GameGallery, decodificação em background, até duas imagens persistentes para transição suave. Um modelo puro controla o ciclo e invalida respostas de uma visita anterior. Uma pequena View própria desenha o contador, atualiza uma vez por segundo e evita redesenhar os cartões do YouTube. A troca direta de fundos teria menor custo, mas a transição breve mantém a leitura suave; carregar toda a galeria na memória aumentaria desnecessariamente o consumo.

Antes do alvo: valores com ao menos dois dígitos no formato DD/HH/MM/SS e rótulos de unidade. No dia19:00/00/00/00 e a frase pedida. A partir do dia20: nenhum bloco ou frase; as fotos continuam alternando. A atualização usa o tempo real, recalculado a cada segundo e ao retomar a home; não acumula segundos em background. Depois de oculto, uma verificação por minuto enquanto a home estiver ativa permite corrigir um relógio que estivesse indevidamente no futuro; não há redesenho do bloco oculto.

Escopo preservado: somente a skin GTA VI muda. Apps, fontes/canais, neon, descanso, skins anteriores, recuperação, licença e créditos permanecem. Versão1.7.0-gta.4/code13, mesmo pacote/chave; atualização install-r sem desinstalar ou limpar dados. Sem modificações diretas na TV pelo agente.
