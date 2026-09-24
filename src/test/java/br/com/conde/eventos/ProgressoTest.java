package br.com.conde.eventos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * As regras de "está em andamento".
 *
 * <p>Esta é a terceira cópia da mesma regra no sistema — ela existe também na
 * API em C# e na tela em JavaScript. A repetição é deliberada, porque cada uma
 * das três precisa aplicá-la sem consultar as outras, mas o preço é que elas
 * podem divergir. Por isso os limiares são constantes públicas e os casos de
 * borda estão escritos aqui: é o que permite conferir uma contra a outra.
 */
class ProgressoTest {

  private static final Instant AGORA = Instant.parse("2026-09-24T18:00:00Z");

  private static Progresso em(double segundo, double duracao) {
    return new Progresso(segundo, duracao, AGORA, false);
  }

  @Test
  @DisplayName("os limiares fazem sentido juntos")
  void limiares() {
    assertTrue(Progresso.FRACAO_MINIMA < Progresso.FRACAO_DE_CONCLUSAO);
    assertTrue(Progresso.SEGUNDOS_MINIMOS > 0);
  }

  @Test
  @DisplayName("dois por cento é o bastante — e só manda em obra curta")
  void comecouPelaFracao() {
    // As duas regras são um OU, então quem manda é a que dispara primeiro.
    // Num filme de uma hora, 2% são 72 segundos e os 30 segundos chegam antes;
    // a fração só decide em obra de menos de 25 minutos, onde 2% é menos que
    // meio minuto. Num curta de 10 minutos, 2% são 12 segundos.
    assertFalse(em(11, 600).emAndamento(), "1,83% e menos de 30 s");
    assertTrue(em(12, 600).emAndamento(), "exatamente 2%");
  }

  @Test
  @DisplayName("trinta segundos também bastam, mesmo numa obra longa")
  void comecouPelosSegundos() {
    // Num filme de três horas, 2% são mais de três minutos. Quem viu meio
    // minuto já escolheu assistir, e a fileira deve lembrar disso.
    assertFalse(em(29, 10800).emAndamento());
    assertTrue(em(30, 10800).emAndamento());
  }

  @Test
  @DisplayName("noventa e dois por cento já é o fim")
  void quaseNoFim() {
    assertTrue(em(3300, 3600).emAndamento(), "91,7%");
    assertFalse(em(3312, 3600).emAndamento(), "92%");
  }

  @Test
  @DisplayName("concluído nunca está em andamento")
  void concluidoSai() {
    assertFalse(new Progresso(1800, 3600, AGORA, true).emAndamento());
  }

  @Test
  @DisplayName("sem duração não dá para dizer, e não dizer é a resposta certa")
  void semDuracao() {
    assertEquals(0, em(1800, 0).fracao());
    assertFalse(em(1800, 0).emAndamento());
    assertEquals(0, em(1800, 0).faltam());
  }

  @Test
  @DisplayName("a fração não passa de um nem quando o segundo passa da duração")
  void fracaoLimitada() {
    // Acontece: o player manda a posição do fim com um arredondamento a mais,
    // e uma fração de 1,002 vira 100,2% na tela.
    assertEquals(1.0, em(3601, 3600).fracao());
    assertEquals(0, em(3601, 3600).faltam());
  }

  @Test
  @DisplayName("quanto falta é o que se mostra na fileira")
  void faltam() {
    assertEquals(1800, em(1800, 3600).faltam());
  }

  @Test
  @DisplayName("as bordas exatas, para conferir contra as outras duas cópias da regra")
  void bordas() {
    record Caso(double segundo, double duracao, boolean esperado, String porque) {}

    List<Caso> casos =
        List.of(
            new Caso(0, 3600, false, "não começou"),
            new Caso(11, 600, false, "curta: 1,83% e menos de 30 s"),
            new Caso(12, 600, true, "curta: exatamente 2%"),
            new Caso(29, 3600, false, "longa: 0,8% e menos de 30 s"),
            new Caso(30, 10800, true, "30 s bastam mesmo em obra longa"),
            new Caso(3311, 3600, true, "91,97%"),
            new Caso(3312, 3600, false, "exatamente 92%"),
            new Caso(3600, 3600, false, "acabou"),
            new Caso(1800, 0, false, "sem duração"));

    for (Caso caso : casos) {
      assertEquals(
          caso.esperado(),
          em(caso.segundo(), caso.duracao()).emAndamento(),
          caso.segundo() + "/" + caso.duracao() + ": " + caso.porque());
    }
  }
}
