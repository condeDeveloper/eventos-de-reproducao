package br.com.conde.eventos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O teste central do projeto.
 *
 * <p>A propriedade que tem de valer: <b>a ordem de chegada não pode mudar o
 * resultado</b>. Se a entrega ordenada e a embaralhada chegam ao mesmo estado,
 * não há como um celular que ficou sem sinal no metrô estragar o progresso de
 * quem o carrega.
 *
 * <p>O oráculo não é um número que escrevi: é a mesma entrada entregue em
 * ordem. Quem tem de concordar com ela é a bagunça.
 */
class OrdemTest {

  private static final Instant AGORA = Instant.parse("2026-09-24T18:00:00Z");

  @Test
  @DisplayName("cem embaralhamentos chegam ao mesmo lugar que a entrega ordenada")
  void cemEmbaralhamentos() {
    List<Evento> eventos = Principal.umaSemana(AGORA);

    Telemetria referencia = new Telemetria(new Audiencia(), Duration.ofDays(8));

    referencia.receberTodos(eventos);

    for (int tentativa = 0; tentativa < 100; tentativa++) {
      List<Evento> baralho = new ArrayList<>(eventos);

      Collections.shuffle(baralho, new Random(tentativa));

      Telemetria fora = new Telemetria(new Audiencia(), Duration.ofDays(8));

      fora.receberTodos(baralho);

      assertTrue(
          Principal.mesmoEstado(referencia, fora),
          "o embaralhamento " + tentativa + " chegou a um estado diferente");
    }
  }

  @Test
  @DisplayName("repetir um em cada cinco eventos não muda nada")
  void repeticaoNaoMudaNada() {
    List<Evento> eventos = Principal.umaSemana(AGORA);

    Telemetria referencia = new Telemetria(new Audiencia(), Duration.ofDays(8));

    referencia.receberTodos(eventos);

    List<Evento> bagunca = Principal.comoARedeEntrega(eventos, new Random(20260924));
    Telemetria real = new Telemetria(new Audiencia(), Duration.ofDays(8));

    real.receberTodos(bagunca);

    assertTrue(real.repetidos() > 1000, "a bagunça precisa mesmo repetir eventos");
    assertEquals(eventos.size(), real.recebidos() - real.repetidos());
    assertTrue(Principal.mesmoEstado(referencia, real));
  }

  @Test
  @DisplayName("o top da semana também não depende da ordem")
  void topNaoDependeDaOrdem() {
    List<Evento> eventos = Principal.umaSemana(AGORA);

    Telemetria referencia = new Telemetria(new Audiencia(), Duration.ofDays(8));

    referencia.receberTodos(eventos);

    for (int tentativa = 0; tentativa < 20; tentativa++) {
      List<Evento> baralho = Principal.comoARedeEntrega(eventos, new Random(tentativa));
      Telemetria fora = new Telemetria(new Audiencia(), Duration.ofDays(8));

      fora.receberTodos(baralho);

      assertEquals(referencia.topDaSemana(10, AGORA), fora.topDaSemana(10, AGORA));
    }
  }

  @Test
  @DisplayName("um evento atrasado não faz a pessoa voltar para o começo")
  void atrasadoNaoRetrocede() {
    // O caso que motiva tudo: o celular ficou sem sinal, guardou "estou em
    // 5:00" e só mandou quando emergiu — depois de o tablet já ter dito
    // "estou em 40:00".
    Telemetria telemetria = new Telemetria();

    Evento adiantado =
        new Evento(
            "tablet-1", 1, 7, 10L, Evento.Tipo.PROGRESSO, 2400, 3600, AGORA.plusSeconds(2400));

    Evento atrasado =
        new Evento("celular-1", 1, 7, 10L, Evento.Tipo.PROGRESSO, 300, 3600, AGORA.plusSeconds(300));

    assertTrue(telemetria.receber(adiantado));
    assertEquals(false, telemetria.receber(atrasado), "o atrasado não pode mudar nada");

    assertEquals(2400, telemetria.progresso(1, 7, 10L).orElseThrow().segundo());
    assertEquals(1, telemetria.atrasadosDescartados());
  }

  @Test
  @DisplayName("dois eventos no mesmo instante: o primeiro vence, e de propósito")
  void empateNaoDeixaARedeDecidir() {
    // Dois eventos no mesmo instante não têm ordem definida. Deixar o último
    // vencer seria deixar a rede decidir — que é justamente o que a regra de
    // ordenação existe para impedir.
    Telemetria telemetria = new Telemetria();
    Instant instante = AGORA.plusSeconds(600);

    telemetria.receber(new Evento("a", 1, 7, null, Evento.Tipo.PROGRESSO, 600, 3600, instante));
    telemetria.receber(new Evento("b", 1, 7, null, Evento.Tipo.PROGRESSO, 100, 3600, instante));

    assertEquals(600, telemetria.progresso(1, 7, null).orElseThrow().segundo());
  }

  @Test
  @DisplayName("quem reassiste volta para continuar assistindo, chegue como chegar")
  void reassistirNaoFicaEscondido() {
    // Este é o caso que derrubou a primeira versão. Ela herdava `concluido` do
    // estado anterior, e com isso: quem terminou na segunda e reassistiu 20%
    // na quarta terminava concluído se os eventos chegassem em ordem, e não
    // concluído se a quarta chegasse antes. Duas respostas para a mesma
    // entrada — e a pegajosa era a errada, porque escondia para sempre da
    // fileira alguém que estava assistindo naquele momento.
    Instant segunda = Instant.parse("2026-09-21T20:00:00Z");

    List<Evento> eventos = new ArrayList<>(new Sessao("seg", 1, 4, null, 600, segunda).inteira());

    eventos.addAll(new Sessao("qua", 1, 4, null, 600, segunda.plus(Duration.ofDays(2))).ate(120, false));

    Telemetria ordenada = new Telemetria();
    Telemetria fora = new Telemetria();

    ordenada.receberTodos(eventos);

    List<Evento> baralho = new ArrayList<>(eventos);

    Collections.shuffle(baralho, new Random(3));
    fora.receberTodos(baralho);

    Progresso progressoOrdenado = ordenada.progresso(1, 4, null).orElseThrow();

    assertEquals(progressoOrdenado, fora.progresso(1, 4, null).orElseThrow());
    assertEquals(false, progressoOrdenado.concluido(), "a sessão de quarta é a mais recente");
    assertEquals(List.of(new ChaveDaObra(4, null)), ordenada.continuarAssistindo(1));
  }

  @Test
  @DisplayName("a janela de repetição precisa ser maior que o atraso, e o teste mede isso")
  void janelaCurtaDeixaPassar() {
    // A janela é a memória do sistema, e escolhê-la é escolher quanto atraso
    // se tolera. Uma janela de seis horas não protege contra uma semana de
    // reordenação — e isso não é bug, é o custo de não guardar tudo.
    List<Evento> eventos = Principal.umaSemana(AGORA);
    List<Evento> bagunca = Principal.comoARedeEntrega(eventos, new Random(20260924));

    Telemetria larga = new Telemetria(new Audiencia(), Duration.ofDays(8));
    Telemetria curta = new Telemetria(new Audiencia(), Duration.ofHours(6));

    larga.receberTodos(bagunca);
    curta.receberTodos(bagunca);

    assertTrue(larga.repetidos() > curta.repetidos() * 5, "a janela curta deixa passar muita coisa");
    assertTrue(curta.idsGuardados() < larga.idsGuardados() / 5, "e em troca guarda muito menos");

    // O progresso sobrevive aos dois: uma repetição que passa tem o mesmo
    // instante do original, então não muda o estado. Quem sofre é a contagem.
    assertTrue(Principal.mesmoEstado(larga, curta));
    assertNotEquals(larga.repetidos(), curta.repetidos());
  }
}
