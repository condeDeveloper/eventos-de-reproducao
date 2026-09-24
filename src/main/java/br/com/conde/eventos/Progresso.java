package br.com.conde.eventos;

import java.time.Instant;

/**
 * Onde um perfil parou numa obra.
 *
 * <p>É o estado que alimenta a fileira "continuar assistindo", e ele é
 * imutável: aplicar um evento devolve um progresso novo. Isso não é purismo —
 * é o que permite comparar o antes e o depois num teste e o que torna a regra
 * de "evento antigo não retrocede" verificável em vez de implícita.
 *
 * @param segundo a posição no vídeo
 * @param duracaoEmSegundos a duração da obra
 * @param atualizadoEm o instante do <b>evento</b> que produziu este estado, não
 *     o da gravação
 * @param concluido se a obra foi terminada
 */
public record Progresso(
    double segundo, double duracaoEmSegundos, Instant atualizadoEm, boolean concluido) {

  /** Abaixo desta fração o evento é clique, não interesse. */
  public static final double FRACAO_MINIMA = 0.02;

  /** A partir desta fração a obra conta como terminada. */
  public static final double FRACAO_DE_CONCLUSAO = 0.92;

  /** Abaixo disto, nem a fração salva: são poucos segundos. */
  public static final double SEGUNDOS_MINIMOS = 30;

  /** Quanto da obra já foi visto, de 0 a 1. */
  public double fracao() {
    if (duracaoEmSegundos <= 0) {
      return 0;
    }

    return Math.min(1.0, segundo / duracaoEmSegundos);
  }

  /**
   * Se isto deve aparecer em "continuar assistindo".
   *
   * <p>A regra tem duas metades, e as duas importam: começou o bastante para
   * não ter sido um clique, e não chegou perto o bastante do fim para já ter
   * acabado. Um catálogo que mostra na fileira o que a pessoa terminou ontem
   * está pedindo para ser ignorado.
   */
  public boolean emAndamento() {
    if (concluido || duracaoEmSegundos <= 0) {
      return false;
    }

    boolean comecou = fracao() >= FRACAO_MINIMA || segundo >= SEGUNDOS_MINIMOS;

    return comecou && fracao() < FRACAO_DE_CONCLUSAO;
  }

  /** Quantos segundos faltam. */
  public double faltam() {
    return Math.max(0, duracaoEmSegundos - segundo);
  }
}
