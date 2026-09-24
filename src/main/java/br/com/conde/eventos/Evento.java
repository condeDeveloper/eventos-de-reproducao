package br.com.conde.eventos;

import java.time.Instant;
import java.util.Objects;

/**
 * Um evento de reprodução, como o player o envia.
 *
 * <p>Há dois instantes aqui, e confundi-los é o erro que estraga toda a
 * telemetria:
 *
 * <ul>
 *   <li>{@link #ocorridoEm()} é <b>quando aconteceu no relógio de quem
 *       assistia</b>. É o que ordena os eventos.
 *   <li>{@link #segundo()} é <b>onde estava no vídeo</b>. É o que vira
 *       "continuar assistindo".
 * </ul>
 *
 * <p>Um evento também traz um {@link #id()}, e ele não é decoração. A entrega é
 * <i>ao menos uma vez</i>: o player reenvia quando não recebe confirmação, a
 * rede duplica, o aplicativo é reaberto e a fila local vai de novo. O mesmo
 * evento chega duas, três, dez vezes. Sem o id não há como saber que é o mesmo,
 * e a contagem de audiência vira ficção.
 *
 * @param id identificador único do evento, gerado pelo player
 * @param perfil quem assistia
 * @param titulo o que assistia
 * @param episodio o episódio, ou {@code null} num filme
 * @param tipo o que aconteceu
 * @param segundo a posição no vídeo, em segundos
 * @param duracaoEmSegundos a duração total da obra
 * @param ocorridoEm quando aconteceu, pelo relógio do aparelho
 */
public record Evento(
    String id,
    long perfil,
    long titulo,
    Long episodio,
    Tipo tipo,
    double segundo,
    double duracaoEmSegundos,
    Instant ocorridoEm) {

  /** O que aconteceu na tela. */
  public enum Tipo {
    /** Começou a tocar. */
    INICIO,
    /** Continua tocando — o batimento periódico. */
    PROGRESSO,
    /** Pausou. */
    PAUSA,
    /** Pulou para outro ponto. */
    SALTO,
    /** Chegou ao fim. */
    FIM
  }

  public Evento {
    Objects.requireNonNull(id, "o evento precisa de id: sem ele não há como descartar repetição");
    Objects.requireNonNull(tipo, "tipo");
    Objects.requireNonNull(ocorridoEm, "ocorridoEm");

    if (id.isBlank()) {
      throw new IllegalArgumentException("o id do evento não pode ser vazio");
    }

    if (segundo < 0) {
      throw new IllegalArgumentException("segundo negativo: " + segundo);
    }

    if (duracaoEmSegundos < 0) {
      throw new IllegalArgumentException("duração negativa: " + duracaoEmSegundos);
    }
  }

  /** Quanto da obra já foi visto, de 0 a 1. */
  public double fracao() {
    if (duracaoEmSegundos <= 0) {
      // Sem duração conhecida não há fração a calcular. Devolver 1 aqui
      // marcaria como concluído algo que talvez mal tenha começado.
      return 0;
    }

    return Math.min(1.0, segundo / duracaoEmSegundos);
  }

  /** A obra específica: um filme, ou um episódio dentro de uma série. */
  public ChaveDaObra obra() {
    return new ChaveDaObra(titulo, episodio);
  }

  /** O par perfil e obra — a chave de "continuar assistindo". */
  public ChaveDoProgresso chave() {
    return new ChaveDoProgresso(perfil, titulo, episodio);
  }
}
