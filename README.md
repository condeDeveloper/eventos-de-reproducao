# eventos-de-reproducao

A telemetria de reprodução do **CondePlay**: os eventos que o player manda
viram "continuar assistindo" e "top 10 da semana".

Java 21, sem uma dependência de produção — só JUnit para testar.

O projeto inteiro existe por causa de um fato do mundo real: **os eventos
chegam repetidos e fora de ordem**. Não é caso excepcional; é o caso comum.

```
$ java -cp target/classes br.com.conde.eventos.Principal

Uma semana de reprodução: 6596 eventos.

  entrega perfeita:   6596 eventos
  entrega de verdade: 7926 eventos, 1330 repetidos vistos, 6384 atrasados descartados, 6596 ids na memória
  com janela de 6h:   7926 eventos, 143 repetidos vistos, 7571 atrasados descartados, 740 ids na memória

  A janela curta deixou passar 1187 repetições: ela esquece o id antes de a cópia chegar.

Top 5 da semana:

  Caçadores de Estática           7 espectadores
  Litoral                         7 espectadores
  Sinal Fraco                     6 espectadores
  O Último Trem para Olinda       5 espectadores
  A Ilha dos Relógios Parados     5 espectadores

Continuar assistindo, perfil 1:

  Dossiê Meia-Noite               63%  faltam 39 min
  Litoral                         38%  faltam 26 min

As duas entregas chegaram ao mesmo lugar? true
```

## As duas coisas que dão errado

### Repetidos

A entrega é *ao menos uma vez*. O player reenvia o que não teve confirmação, o
aplicativo reaberto despeja a fila local de novo, a rede duplica. Contar duas
vezes o mesmo evento infla a audiência de quem tem conexão ruim — que é
exatamente quem reenvia mais.

A defesa é um id por evento e um conjunto dos já vistos. Não dá para descartar
por "evento parecido": dois batimentos podem ter o mesmo perfil, título,
segundo e até instante, e ainda assim serem dois.

### Fora de ordem

O celular ficou sem sinal no metrô e mandou tudo junto ao emergir; o tablet, na
mesma casa, já tinha mandado. Um evento de "estou em 5:00" chega **depois** de
um "estou em 40:00".

Gravar o último que chegou faz a pessoa voltar para o começo do episódio sem
entender por quê. É a falha mais irritante possível num serviço de streaming,
porque parece que o sistema esqueceu.

A defesa é comparar por `ocorridoEm` antes de gravar — e descartar o empate
também, porque dois eventos no mesmo instante não têm ordem definida e "o
último que chegou" seria deixar a rede decidir.

## O teste que define o projeto

A propriedade: **a ordem de chegada não pode mudar o resultado.**

O oráculo não é um número que eu escrevi. É a mesma entrada entregue em ordem
perfeita — e quem tem de concordar com ela é a bagunça. Cem embaralhamentos
diferentes de uma semana de eventos, mais a duplicação de um em cada cinco,
todos chegando ao mesmo estado final.

## O bug que esse teste encontrou

A primeira versão guardava `concluido` como estado pegajoso: uma vez concluído,
concluído para sempre.

```java
boolean concluido = evento.tipo() == FIM
    || evento.fracao() >= FRACAO_DE_CONCLUSAO
    || (atual != null && atual.concluido());   // ← isto
```

Parecia razoável. Estava errado duas vezes.

**Dependia da ordem de chegada.** Quem terminou na segunda e reassistiu 20% na
quarta terminava assim:

```
em ordem:     Progresso[segundo=120.0, ..., concluido=true]
embaralhado:  Progresso[segundo=120.0, ..., concluido=false]
```

Porque na entrega embaralhada a sessão de quarta chegava primeiro, e a de
segunda — com o `FIM` — era descartada por atraso e nunca contribuía.

**E a resposta pegajosa era a pior das duas.** Quem reassiste está assistindo
*agora*; a conclusão da semana passada não pode esconder isso da fileira.
Derivar `concluido` só do evento mais recente conserta as duas de uma vez.

Duas respostas para a mesma entrada é o tipo de coisa que não aparece em teste
de exemplo — só em teste de propriedade.

## O balde de hora

O "top 10 da semana" precisa de uma janela que anda, e há três formas de fazer:

| | custo | problema |
|---|---|---|
| guardar cada evento com seu instante | milhões de linhas varridas por consulta | não escala |
| um contador por título | uma linha por título | sem instante não dá para tirar o que saiu da janela: o "top da semana" vira "top desde sempre", e nenhum lançamento alcança |
| **balde de hora** | 168 baldes por semana | perde saber que foi às 14h37 e não às 14h — o que não muda nada numa fileira semanal |

E a contagem é de **perfis distintos por balde**, não de eventos: um batimento
a cada trinta segundos geraria cento e vinte "visualizações" por hora de quem
assistiu uma vez.

A janela é fechada no fim e aberta no começo. Sem essa regra, uma consulta às
14h00 e outra às 14h01 olhariam conjuntos diferentes de baldes, e o ranque
saltaria na virada da hora sem ninguém ter assistido nada.

## A janela de repetição é uma escolha, e o custo dela é medido

Guardar ids para sempre resolve a repetição e nunca acaba. A janela é a memória
do sistema, e escolhê-la é escolher **quanto atraso se está disposto a
tolerar** — o que a saída acima mostra em números:

```
janela de 8 dias:  1330 repetições pegas, 6596 ids na memória
janela de 6 horas:  143 repetições pegas,  740 ids na memória
```

Uma janela de seis horas não protege contra uma semana de reordenação: ela
esquece o id antes de a cópia chegar. Isso não é bug, é o preço de não guardar
tudo — e um teste guarda os dois números para que a escolha continue explícita.

O progresso sobrevive às duas, porque uma repetição que escapa tem o mesmo
instante do original e não muda o estado. Quem sofre é a contagem de audiência.

## Rodando

```bash
mvn test
mvn -q compile && java -cp target/classes br.com.conde.eventos.Principal
```

46 testes. O mais demorado são os cem embaralhamentos, que levam meio minuto —
é o preço de testar uma propriedade em vez de um exemplo.

Como biblioteca:

```java
Telemetria telemetria = new Telemetria();

telemetria.receber(new Evento(
    "evt-1", /* perfil */ 1, /* titulo */ 7, /* episodio */ 3L,
    Evento.Tipo.PROGRESSO, /* segundo */ 1200, /* duracao */ 2520, Instant.now()));

telemetria.continuarAssistindo(1);          // [título 7, episódio 3]
telemetria.progresso(1, 7, 3L);             // Optional[Progresso[...]]
telemetria.topDaSemana(10, Instant.now());  // [Posicao[titulo=4, espectadores=7], ...]
```

## Estrutura

```
Evento.java            o que o player manda — e os dois instantes que não se confundem
ChaveDaObra.java       título e episódio; num filme o episódio é null, e não zero
ChaveDoProgresso.java  perfil, título e episódio: o motivo de o perfil existir
Progresso.java         onde a pessoa parou, e se isso vai para a fileira
Telemetria.java        recebe eventos: descarta repetido, descarta atrasado
Audiencia.java         o balde de hora e a janela que anda
Sessao.java            monta a sequência que um player produz de verdade
Principal.java         a demonstração acima
```

## Onde ele se encaixa

Faz parte do **CondePlay**, um serviço de streaming montado em peças separadas:

| | |
|---|---|
| [`catalogo`](https://github.com/condeDeveloper/catalogo) | a API do catálogo, em C# e .NET 8 |
| [`player-hls`](https://github.com/condeDeveloper/player-hls) | o player HLS, do zero |
| [`conde-play`](https://github.com/condeDeveloper/conde-play) | a tela |
| [`mp4`](https://github.com/condeDeveloper/mp4) | os metadados da mídia |
| [`recomendacoes`](https://github.com/condeDeveloper/recomendacoes) | o "porque você assistiu X" |
| **`eventos-de-reproducao`** | a telemetria que alimenta os dois últimos |

A regra de "está em andamento" existe **três vezes** no sistema: aqui, na API
em C# e na tela em JavaScript. A repetição é deliberada — cada uma precisa
aplicá-la sem consultar as outras —, mas o preço é que elas podem divergir. Por
isso os limiares são constantes públicas e as bordas exatas estão escritas em
`ProgressoTest.bordas()`: é o que permite conferir uma contra a outra.

## O conteúdo é próprio

Os títulos da demonstração são **inventados para este projeto** e são os mesmos
que a API semeia. Os eventos são gerados, não capturados: não há dado de
reprodução de pessoa nenhuma neste repositório.

## Limites conhecidos

- **Tudo em memória, num processo só.** Não há persistência nem partição. Um
  serviço de verdade guarda o progresso num banco e roda a agregação em
  paralelo, e aí a deduplicação precisa ser coordenada entre os nós — que é um
  problema bem maior do que um `HashSet`.
- **Nada é thread-safe.** `Telemetria` e `Audiencia` supõem um chamador por
  vez.
- **O relógio é o do aparelho.** Um celular com a data errada manda eventos do
  futuro, e eles vencem todos os outros para sempre. O contador
  `atrasadosDescartados()` é o sintoma que denuncia isso, mas não há correção.
- **Sem marca d'água.** Não existe um ponto a partir do qual a janela é
  declarada fechada; eventos muito antigos simplesmente não mudam mais nada.
- **A audiência não distingue quem assistiu um minuto de quem assistiu tudo.**
  Aparecer no balde basta. Ponderar por tempo assistido daria um ranque melhor
  e um "top" que muda mais devagar.
- **`esquecerAntesDe` não é chamado sozinho.** Quem usa a biblioteca decide
  quando podar; sem podar, a memória cresce com o tempo.

## Licença

MIT.
