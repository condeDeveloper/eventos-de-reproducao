package br.com.conde.eventos;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Uma demonstração que se pode rodar e ler.
 *
 * <pre>
 *   mvn -q compile exec:java
 *   java -cp target/classes br.com.conde.eventos.Principal
 * </pre>
 *
 * <p>Ela monta uma semana de reprodução do catálogo de demonstração, embaralha
 * os eventos, duplica um em cada cinco — como a rede faz — e mostra que o
 * resultado é o mesmo de uma entrega perfeita.
 */
public final class Principal {

  /** Os títulos do catálogo, com nome e duração em segundos. */
  private static final String[] NOMES = {
    "Cidade de Vidro",
    "O Último Trem para Olinda",
    "Enquanto a Chuva Não Passa",
    "Caçadores de Estática",
    "A Ilha dos Relógios Parados",
    "Dossiê Meia-Noite",
    "Litoral",
    "Sinal Fraco"
  };

  private static final double[] DURACOES = {
    118 * 60, 96 * 60, 84 * 60, 131 * 60, 77 * 60, 104 * 60, 42 * 60, 45 * 60
  };

  private Principal() {}

  public static void main(String[] argumentos) {
    Instant agora = Instant.parse("2026-09-24T18:00:00Z");
    List<Evento> eventos = umaSemana(agora);

    System.out.println("Uma semana de reprodução: " + eventos.size() + " eventos.\n");

    // 1. A entrega perfeita: em ordem, sem repetição. É a referência.
    Telemetria referencia = new Telemetria();

    referencia.receberTodos(eventos);

    // 2. A entrega de verdade: fora de ordem e com repetição.
    List<Evento> bagunca = comoARedeEntrega(eventos, new Random(20260924));
    Telemetria real = new Telemetria(new Audiencia(), Duration.ofDays(8));

    real.receberTodos(bagunca);

    // 3. A mesma entrega, com a janela de repetição curta demais para o
    // atraso que os eventos trazem. Ela é o tamanho da memória do sistema, e
    // escolhê-la é escolher quanto atraso se está disposto a tolerar.
    Telemetria janelaCurta = new Telemetria(new Audiencia(), Duration.ofHours(6));

    janelaCurta.receberTodos(bagunca);

    System.out.println("  entrega perfeita:   " + referencia.recebidos() + " eventos");
    System.out.printf(
        "  entrega de verdade: %d eventos, %d repetidos vistos, %d atrasados descartados, %d ids na memória%n",
        real.recebidos(), real.repetidos(), real.atrasadosDescartados(), real.idsGuardados());
    System.out.printf(
        "  com janela de 6h:   %d eventos, %d repetidos vistos, %d atrasados descartados, %d ids na memória%n",
        janelaCurta.recebidos(),
        janelaCurta.repetidos(),
        janelaCurta.atrasadosDescartados(),
        janelaCurta.idsGuardados());
    System.out.println(
        "\n  A janela curta deixou passar "
            + (real.repetidos() - janelaCurta.repetidos())
            + " repetições: ela esquece o id antes de a cópia chegar.");

    System.out.println("\nTop 5 da semana:\n");

    for (Audiencia.Posicao posicao : real.topDaSemana(5, agora)) {
      System.out.printf(
          "  %-30s %2d espectadores%n", NOMES[(int) posicao.titulo() - 1], posicao.espectadores());
    }

    System.out.println("\nContinuar assistindo, perfil 1:\n");

    for (ChaveDaObra obra : real.continuarAssistindo(1)) {
      Progresso progresso = real.progresso(1, obra.titulo(), obra.episodio()).orElseThrow();

      System.out.printf(
          "  %-30s %3.0f%%  faltam %.0f min%n",
          NOMES[(int) obra.titulo() - 1], progresso.fracao() * 100, progresso.faltam() / 60);
    }

    System.out.println("\nAs duas entregas chegaram ao mesmo lugar? " + mesmoEstado(referencia, real));
  }

  /** Uma semana de reprodução do catálogo. */
  static List<Evento> umaSemana(Instant agora) {
    List<Evento> eventos = new ArrayList<>();
    Random sorteio = new Random(7);

    for (int dia = 6; dia >= 0; dia--) {
      Instant inicioDoDia = agora.minus(Duration.ofDays(dia));

      for (long perfil = 1; perfil <= 12; perfil++) {
        // Nem todo perfil assiste todo dia.
        if (sorteio.nextDouble() < 0.4) {
          continue;
        }

        int titulo = 1 + sorteio.nextInt(NOMES.length);
        double duracao = DURACOES[titulo - 1];
        // Uns terminam, outros param no meio: é o que alimenta a fileira.
        double ate = sorteio.nextDouble() < 0.45 ? duracao : duracao * (0.1 + sorteio.nextDouble() * 0.7);

        Sessao sessao =
            new Sessao(
                "d" + dia + "p" + perfil,
                perfil,
                titulo,
                null,
                duracao,
                inicioDoDia.plusSeconds(sorteio.nextInt(8 * 3600)));

        eventos.addAll(sessao.ate(ate, ate >= duracao));
      }
    }

    return eventos;
  }

  /** Embaralha e duplica, como a rede faz. */
  static List<Evento> comoARedeEntrega(List<Evento> eventos, Random sorteio) {
    List<Evento> bagunca = new ArrayList<>(eventos);

    for (Evento evento : eventos) {
      if (sorteio.nextDouble() < 0.2) {
        bagunca.add(evento);
      }
    }

    Collections.shuffle(bagunca, sorteio);

    return bagunca;
  }

  /** Se duas telemetrias chegaram ao mesmo progresso para todo mundo. */
  static boolean mesmoEstado(Telemetria uma, Telemetria outra) {
    if (!uma.perfis().equals(outra.perfis())) {
      return false;
    }

    for (long perfil : uma.perfis()) {
      if (!uma.continuarAssistindo(perfil).equals(outra.continuarAssistindo(perfil))) {
        return false;
      }
    }

    return true;
  }
}
