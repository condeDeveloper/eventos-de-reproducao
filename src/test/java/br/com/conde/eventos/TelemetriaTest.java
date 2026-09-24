package br.com.conde.eventos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A telemetria: repetição, progresso e a fileira "continuar assistindo". */
class TelemetriaTest {

  private static final Instant AGORA = Instant.parse("2026-09-24T18:00:00Z");

  private static Evento evento(String id, long perfil, long titulo, double segundo, Instant quando) {
    return new Evento(id, perfil, titulo, null, Evento.Tipo.PROGRESSO, segundo, 3600, quando);
  }

  @Test
  @DisplayName("o mesmo evento duas vezes conta uma")
  void repeticao() {
    Telemetria telemetria = new Telemetria();
    Evento um = evento("a", 1, 7, 600, AGORA);

    assertTrue(telemetria.receber(um));
    assertFalse(telemetria.receber(um));
    assertFalse(telemetria.receber(um));

    assertEquals(3, telemetria.recebidos());
    assertEquals(2, telemetria.repetidos());
  }

  @Test
  @DisplayName("eventos parecidos com ids diferentes são eventos diferentes")
  void parecidoNaoERepetido() {
    // Dois batimentos podem ter o mesmo perfil, título, segundo e até
    // instante, e ainda assim serem dois. É o id que diz que é o mesmo.
    Telemetria telemetria = new Telemetria();

    telemetria.receber(evento("a", 1, 7, 600, AGORA));
    telemetria.receber(evento("b", 1, 7, 600, AGORA));

    assertEquals(0, telemetria.repetidos());
  }

  @Test
  @DisplayName("o id vazio é recusado na origem")
  void idObrigatorio() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new Evento("", 1, 7, null, Evento.Tipo.INICIO, 0, 3600, AGORA));

    assertThrows(
        NullPointerException.class,
        () -> new Evento(null, 1, 7, null, Evento.Tipo.INICIO, 0, 3600, AGORA));
  }

  @Test
  @DisplayName("o progresso avança com o tempo do evento")
  void progressoAvanca() {
    Telemetria telemetria = new Telemetria();

    telemetria.receber(evento("a", 1, 7, 300, AGORA));
    telemetria.receber(evento("b", 1, 7, 900, AGORA.plusSeconds(600)));

    assertEquals(900, telemetria.progresso(1, 7, null).orElseThrow().segundo());
  }

  @Test
  @DisplayName("cada perfil tem o seu ponto na mesma obra")
  void perfisNaoSeMisturam() {
    // Duas pessoas na mesma casa assistindo a mesma série em pontos
    // diferentes é o motivo de o perfil existir.
    Telemetria telemetria = new Telemetria();

    telemetria.receber(evento("a", 1, 7, 300, AGORA));
    telemetria.receber(evento("b", 2, 7, 2400, AGORA));

    assertEquals(300, telemetria.progresso(1, 7, null).orElseThrow().segundo());
    assertEquals(2400, telemetria.progresso(2, 7, null).orElseThrow().segundo());
  }

  @Test
  @DisplayName("episódios da mesma série são obras distintas")
  void episodiosNaoSeMisturam() {
    Telemetria telemetria = new Telemetria();

    telemetria.receber(
        new Evento("a", 1, 7, 1L, Evento.Tipo.PROGRESSO, 300, 2520, AGORA));
    telemetria.receber(
        new Evento("b", 1, 7, 2L, Evento.Tipo.PROGRESSO, 1200, 2520, AGORA.plusSeconds(3000)));

    assertEquals(300, telemetria.progresso(1, 7, 1L).orElseThrow().segundo());
    assertEquals(1200, telemetria.progresso(1, 7, 2L).orElseThrow().segundo());
  }

  @Test
  @DisplayName("um filme e um episódio de id igual não colidem")
  void filmeNaoColideComEpisodio() {
    // Tratar "sem episódio" como episódio zero faria todos os filmes do
    // catálogo caírem no mesmo balde.
    Telemetria telemetria = new Telemetria();

    telemetria.receber(new Evento("a", 1, 7, null, Evento.Tipo.PROGRESSO, 300, 3600, AGORA));
    telemetria.receber(new Evento("b", 1, 7, 0L, Evento.Tipo.PROGRESSO, 1800, 3600, AGORA));

    assertEquals(300, telemetria.progresso(1, 7, null).orElseThrow().segundo());
    assertEquals(1800, telemetria.progresso(1, 7, 0L).orElseThrow().segundo());
  }

  @Test
  @DisplayName("quem só clicou não entra na fileira")
  void cliqueNaoEntra() {
    Telemetria telemetria = new Telemetria();

    telemetria.receber(evento("a", 1, 7, 10, AGORA));

    assertEquals(List.of(), telemetria.continuarAssistindo(1));
  }

  @Test
  @DisplayName("quem terminou sai da fileira")
  void terminadoSai() {
    // Uma fileira que mostra o que a pessoa terminou ontem é uma fileira que
    // ela aprende a ignorar.
    Telemetria telemetria = new Telemetria();

    telemetria.receber(
        new Evento("a", 1, 7, null, Evento.Tipo.FIM, 3600, 3600, AGORA));

    assertTrue(telemetria.progresso(1, 7, null).orElseThrow().concluido());
    assertEquals(List.of(), telemetria.continuarAssistindo(1));
  }

  @Test
  @DisplayName("quem chegou a 92% conta como terminado mesmo sem evento de FIM")
  void quaseNoFimContaComoTerminado() {
    // Ninguém assiste os créditos. Exigir o FIM deixaria na fileira coisa que
    // acabou, e o player nem sempre consegue mandar o último evento.
    Telemetria telemetria = new Telemetria();

    telemetria.receber(evento("a", 1, 7, 3400, AGORA));

    assertTrue(telemetria.progresso(1, 7, null).orElseThrow().concluido());
  }

  @Test
  @DisplayName("a fileira vem do mais recente para o mais antigo")
  void fileiraOrdenada() {
    Telemetria telemetria = new Telemetria();

    telemetria.receber(evento("a", 1, 1, 1800, AGORA.minus(Duration.ofDays(3))));
    telemetria.receber(evento("b", 1, 2, 1800, AGORA.minus(Duration.ofHours(2))));
    telemetria.receber(evento("c", 1, 3, 1800, AGORA.minus(Duration.ofDays(1))));

    assertEquals(
        List.of(new ChaveDaObra(2, null), new ChaveDaObra(3, null), new ChaveDaObra(1, null)),
        telemetria.continuarAssistindo(1));
  }

  @Test
  @DisplayName("empate na fileira desempata pelo título")
  void fileiraEstavel() {
    Telemetria telemetria = new Telemetria();

    telemetria.receber(evento("a", 1, 8, 1800, AGORA));
    telemetria.receber(evento("b", 1, 2, 1800, AGORA));
    telemetria.receber(evento("c", 1, 5, 1800, AGORA));

    List<ChaveDaObra> fileira = telemetria.continuarAssistindo(1);

    assertEquals(
        List.of(new ChaveDaObra(2, null), new ChaveDaObra(5, null), new ChaveDaObra(8, null)),
        fileira);

    for (int i = 0; i < 20; i++) {
      assertEquals(fileira, telemetria.continuarAssistindo(1));
    }
  }

  @Test
  @DisplayName("a fileira de um perfil não mostra a de outro")
  void fileiraPorPerfil() {
    Telemetria telemetria = new Telemetria();

    telemetria.receber(evento("a", 1, 1, 1800, AGORA));
    telemetria.receber(evento("b", 2, 2, 1800, AGORA));

    assertEquals(List.of(new ChaveDaObra(1, null)), telemetria.continuarAssistindo(1));
    assertEquals(List.of(new ChaveDaObra(2, null)), telemetria.continuarAssistindo(2));
    assertEquals(List.of(), telemetria.continuarAssistindo(99));
  }

  @Test
  @DisplayName("sem duração conhecida, nada entra na fileira")
  void semDuracao() {
    // Sem duração não dá para dizer se está no meio ou no fim. Chutar "no
    // meio" prende na fileira algo que talvez já tenha acabado.
    Telemetria telemetria = new Telemetria();

    telemetria.receber(
        new Evento("a", 1, 7, null, Evento.Tipo.PROGRESSO, 1800, 0, AGORA));

    assertEquals(List.of(), telemetria.continuarAssistindo(1));
    assertEquals(0, telemetria.progresso(1, 7, null).orElseThrow().fracao());
  }

  @Test
  @DisplayName("perguntar por quem não existe devolve vazio, não nulo")
  void perguntaSemResposta() {
    Telemetria telemetria = new Telemetria();

    assertTrue(telemetria.progresso(1, 7, null).isEmpty());
    assertEquals(List.of(), telemetria.continuarAssistindo(1));
    assertEquals(List.of(), telemetria.topDaSemana(10, AGORA));
  }

  @Test
  @DisplayName("valores impossíveis são recusados na porta")
  void validacao() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new Evento("a", 1, 7, null, Evento.Tipo.INICIO, -1, 3600, AGORA));

    assertThrows(
        IllegalArgumentException.class,
        () -> new Evento("a", 1, 7, null, Evento.Tipo.INICIO, 0, -3600, AGORA));

    assertThrows(NullPointerException.class, () -> new Telemetria().receber(null));
  }

  @Test
  @DisplayName("uma sessão inteira deixa a obra concluída e fora da fileira")
  void sessaoCompleta() {
    Telemetria telemetria = new Telemetria();
    Sessao sessao = new Sessao("s", 1, 7, 3L, 2520, AGORA);

    int mudaram = telemetria.receberTodos(sessao.inteira());

    assertEquals(mudaram, telemetria.recebidos(), "em ordem, todo evento avança o progresso");
    assertTrue(telemetria.progresso(1, 7, 3L).orElseThrow().concluido());
    assertEquals(List.of(), telemetria.continuarAssistindo(1));
  }

  @Test
  @DisplayName("uma sessão interrompida no meio fica na fileira, com o quanto falta")
  void sessaoInterrompida() {
    Telemetria telemetria = new Telemetria();

    telemetria.receberTodos(new Sessao("s", 1, 7, 3L, 2520, AGORA).ate(1200, false));

    Progresso progresso = telemetria.progresso(1, 7, 3L).orElseThrow();

    assertFalse(progresso.concluido());
    assertTrue(progresso.emAndamento());
    assertEquals(1320, progresso.faltam());
    assertEquals(List.of(new ChaveDaObra(7, 3L)), telemetria.continuarAssistindo(1));
  }
}
