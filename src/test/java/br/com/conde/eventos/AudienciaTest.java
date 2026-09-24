package br.com.conde.eventos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A contagem por balde de hora e a janela que anda. */
class AudienciaTest {

  private static final Instant AGORA = Instant.parse("2026-09-24T18:00:00Z");

  private static Evento assistiu(long perfil, long titulo, Instant quando) {
    return new Evento(
        perfil + "-" + titulo + "-" + quando, perfil, titulo, null, Evento.Tipo.PROGRESSO, 600, 3600, quando);
  }

  @Test
  @DisplayName("conta pessoas, não eventos")
  void contaPessoas() {
    // Um batimento a cada trinta segundos gera cento e vinte eventos por hora.
    // Contá-los como visualizações faria uma pessoa parecer uma multidão.
    Audiencia audiencia = new Audiencia();

    for (int i = 0; i < 120; i++) {
      audiencia.contar(assistiu(1, 7, AGORA.minusSeconds(i * 30L)));
    }

    audiencia.contar(assistiu(2, 7, AGORA));

    assertEquals(List.of(new Audiencia.Posicao(7, 2)), audiencia.top(10, AGORA, Duration.ofDays(7)));
  }

  @Test
  @DisplayName("um salto é navegação, não audiência")
  void saltoNaoConta() {
    // Quem arrasta a barra procurando uma cena não está assistindo. Contar
    // isso premiaria o vídeo em que é difícil achar o que se procura.
    Audiencia audiencia = new Audiencia();

    audiencia.contar(
        new Evento("s", 1, 7, null, Evento.Tipo.SALTO, 600, 3600, AGORA));

    assertEquals(List.of(), audiencia.top(10, AGORA, Duration.ofDays(7)));
  }

  @Test
  @DisplayName("o que saiu da janela sai do ranque")
  void janelaAnda() {
    Audiencia audiencia = new Audiencia();

    audiencia.contar(assistiu(1, 1, AGORA.minus(Duration.ofDays(10))));
    audiencia.contar(assistiu(2, 2, AGORA.minus(Duration.ofDays(2))));

    List<Audiencia.Posicao> semana = audiencia.top(10, AGORA, Duration.ofDays(7));

    assertEquals(1, semana.size());
    assertEquals(2, semana.get(0).titulo(), "o de dez dias atrás ficou de fora");

    assertEquals(2, audiencia.top(10, AGORA, Duration.ofDays(30)).size());
  }

  @Test
  @DisplayName("a janela é fechada no fim e aberta no começo")
  void bordasDaJanela() {
    // Sem uma regra explícita de borda, uma consulta às 14h00 e outra às 14h01
    // olhariam conjuntos diferentes de baldes, e o ranque saltaria na virada
    // da hora sem ninguém ter assistido nada.
    Audiencia audiencia = new Audiencia();
    Duration janela = Duration.ofHours(3);

    audiencia.contar(assistiu(1, 1, AGORA));
    audiencia.contar(assistiu(2, 2, AGORA.minus(janela)));

    List<Audiencia.Posicao> ranque = audiencia.top(10, AGORA, janela);

    assertEquals(1, ranque.size(), "o balde que completa a janela fica de fora");
    assertEquals(1, ranque.get(0).titulo());
  }

  @Test
  @DisplayName("consultas dentro da mesma hora dão o mesmo resultado")
  void estavelDentroDaHora() {
    Audiencia audiencia = new Audiencia();

    audiencia.contar(assistiu(1, 1, AGORA));
    audiencia.contar(assistiu(2, 2, AGORA.minus(Duration.ofDays(3))));

    List<Audiencia.Posicao> noPonto = audiencia.top(10, AGORA, Duration.ofDays(7));

    for (int minuto = 1; minuto < 60; minuto++) {
      assertEquals(noPonto, audiencia.top(10, AGORA.plusSeconds(minuto * 60L), Duration.ofDays(7)));
    }
  }

  @Test
  @DisplayName("empate desempata pelo id, para a fileira não se mexer sozinha")
  void empateEstavel() {
    Audiencia audiencia = new Audiencia();

    audiencia.contar(assistiu(1, 8, AGORA));
    audiencia.contar(assistiu(1, 3, AGORA));
    audiencia.contar(assistiu(1, 5, AGORA));

    List<Audiencia.Posicao> ranque = audiencia.top(10, AGORA, Duration.ofDays(7));

    assertEquals(List.of(3L, 5L, 8L), ranque.stream().map(Audiencia.Posicao::titulo).toList());

    for (int i = 0; i < 20; i++) {
      assertEquals(ranque, audiencia.top(10, AGORA, Duration.ofDays(7)));
    }
  }

  @Test
  @DisplayName("esquecer o passado limita a memória")
  void esquecer() {
    // Sem isto a estrutura cresce para sempre. Com isto, o tamanho máximo é
    // conhecido: um balde por hora da janela mais longa que se consulta.
    Audiencia audiencia = new Audiencia();

    for (int hora = 0; hora < 240; hora++) {
      audiencia.contar(assistiu(1, 1, AGORA.minus(Duration.ofHours(hora))));
    }

    assertEquals(240, audiencia.baldes());

    int jogados = audiencia.esquecerAntesDe(AGORA.minus(Duration.ofDays(7)));

    // 169, e não 168: o corte é exclusivo, então o balde que fica exatamente
    // na borda sobrevive. `top` já o exclui da conta, de forma que o que
    // sobra é um balde a mais do que a janela usa — e é assim de propósito.
    // Errar para o lado de guardar demais custa uma hora de memória; errar
    // para o lado de jogar fora demais custa um dado que a janela ainda quer.
    assertEquals(240 - 169, jogados);
    assertEquals(169, audiencia.baldes());
  }

  @Test
  @DisplayName("sem nada guardado, nada quebra")
  void vazia() {
    Audiencia audiencia = new Audiencia();

    assertEquals(List.of(), audiencia.top(10, AGORA, Duration.ofDays(7)));
    assertEquals(0, audiencia.baldes());
    assertNull(audiencia.maisAntigo());
    assertEquals(0, audiencia.esquecerAntesDe(AGORA));
  }

  @Test
  @DisplayName("pedir zero ou menos devolve lista vazia")
  void quantosInvalido() {
    Audiencia audiencia = new Audiencia();

    audiencia.contar(assistiu(1, 1, AGORA));

    assertEquals(List.of(), audiencia.top(0, AGORA, Duration.ofDays(7)));
    assertEquals(List.of(), audiencia.top(-3, AGORA, Duration.ofDays(7)));
  }

  @Test
  @DisplayName("o corte respeita o tamanho do ranque")
  void cortePorTamanho() {
    Audiencia audiencia = new Audiencia();

    for (long titulo = 1; titulo <= 8; titulo++) {
      audiencia.contar(assistiu(titulo, titulo, AGORA));
    }

    assertEquals(3, audiencia.top(3, AGORA, Duration.ofDays(7)).size());
    assertEquals(8, audiencia.top(100, AGORA, Duration.ofDays(7)).size());
  }

  @Test
  @DisplayName("o mesmo perfil em horas diferentes continua sendo um espectador")
  void mesmoPerfilEmBaldesDiferentes() {
    // Ele aparece em dois baldes, e a soma da janela precisa juntar os
    // conjuntos, não os tamanhos — senão maratonar vira audiência.
    Audiencia audiencia = new Audiencia();

    audiencia.contar(assistiu(1, 7, AGORA));
    audiencia.contar(assistiu(1, 7, AGORA.minus(Duration.ofHours(3))));

    assertEquals(
        List.of(new Audiencia.Posicao(7, 1)), audiencia.top(10, AGORA, Duration.ofDays(7)));
  }

  @Test
  @DisplayName("a granularidade é de hora, e isso está declarado")
  void granularidade() {
    Audiencia audiencia = new Audiencia();

    audiencia.contar(assistiu(1, 1, Instant.parse("2026-09-24T18:03:00Z")));
    audiencia.contar(assistiu(2, 1, Instant.parse("2026-09-24T18:57:00Z")));

    assertEquals(1, audiencia.baldes(), "os dois caem no mesmo balde");
    assertTrue(audiencia.maisAntigo().equals(Instant.parse("2026-09-24T18:00:00Z")));
  }
}
