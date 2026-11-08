# Verificação da versão1.7.0-gta.4

Base imediata: gta.3, commit `cf9e16f6088302b24858d9da6e2f1fbaf7447481`. Base original ViktorKav: `4b95e7451628e6655a14d3d5596083adb257c14b`. Pacote `io.freemytv`, Android12+, código de versão13.

## Executado

- `bash tests/run-java-tests.sh`:1.164 verificações aprovadas. Os47 novos casos de calendário/ciclo cobrem o instante antes do alvo, as duas viradas em Brasília, dia de lançamento completo, independência do fuso padrão, recálculo após correção de relógio, intervalo15s, falhas individuais/totais, imagem única sobrevivente e descarte de conclusões antigas após pausa/retomada.
- `python3 -m unittest discover -s tests -p 'test_*.py' -q`:43 testes aprovados.
- `bash build.sh`: build com JDK17/Android34 aprovado. Avisos de APIs obsoletas não impedem a compilação.
- `apksigner verify --verbose --print-certs` e `zipalign -c 4`: aprovados. Certificado SHA-256 `f555ccc7fe855e1bcf8c7bc96eecc523838dc856aaa5a0b3388a6ef89aad7308`, igual às versões anteriores.
- Revisão independente: calendário Brasília, ciclo/gerações, descarte de bitmaps, pausa/retomada, camadas e limite de memória. Sem achados críticos/importantes. Uma correção de relógio para trás após expirar o contador passa a ser percebida por uma verificação por minuto do bloco oculto; a pausa remove o timer. Esse pequeno ajuste de agenda da View foi compilado após a revisão; não se afirma teste Android de interface.
- Fotos/logo existentes, MIT e créditos preservados. Nenhum favorito pessoal, chave privada ou configuração de feed pessoal no APK/fontes públicos.
- `HomeMode.java`, `Adb.java`, `ModeJob.java`, `Watchdog.java`, `HomeReceiver.java`, `Screensaver.java` e `LICENSE` iguais à base original. Não há permissões novas nem mudanças em preferências.

## Evidência do aparelho e limites

O usuário confirmou instalação/galeria/retorno a Original anteriores e enviou fotos dos vídeos públicos funcionando após a gta.3. Isso não valida os novos recursos da gta.4.

Não houve acesso direto à TCL, instalação pelo agente, emulador ou teste visual/stress nativo da nova View. A aparência real, fluidez, consumo de memória no aparelho e o retorno de outros apps precisam do teste pelo controle descrito no [guia](INSTRUCOES-INSTALACAO.md). A versão segue experimental até essa conferência.

O contador tem como alvo a data fornecida pelo usuário,19/11/2026 às00:00 Brasília; não consulta notícias ou API de lançamento. No dia19 mostra zeros e a frase solicitada; a partir de20 fica oculto. Depende de o relógio da TV estar correto. As fronteiras de calendário foram verificadas por modelo puro, não alterando o relógio da TV.

## Atualizar e voltar

Mesmo pacote/chave; atualizar gta.1/gta.2/gta.3 com `install -r`, sem desinstalar ou limpar dados. O mecanismo de retorno é o mesmo: **Free My TV → Início → Original**, aguardar e conferir com Home. O agente não modifica o aparelho.
