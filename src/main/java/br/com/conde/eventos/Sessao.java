package br.com.conde.eventos;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Monta a sequência de eventos que uma sessão de reprodução produz.
 *
 * <p>Um player não manda um evento por sessão: manda um {@code INICIO}, um
 * batimento a cada trinta segundos enquanto toca, e um {@code PAUSA} ou
 * {@code FIM} no final. Uma hora de vídeo são cento e vinte eventos.
 *
 * <p>Gerar esse fluxo de verdade — em vez de inventar três eventos soltos nos
 * testes — é o que faz aparecerem os problemas reais: o volume de repetição, o
 * que acontece quando o meio da sequência chega fora de ordem, e a diferença
 * entre contar eventos e contar pessoas.
 */
public final class Sessao {

  /** O intervalo entre batimentos, como o player envia. */
  public static final Duration BATIMENTO = Duration.ofSeconds(30);

  private final long perfil;
  private final long titulo;
  private final Long episodio;
  private final double duracaoEmSegundos;
  private final Instant comecouEm;
  private final String prefixo;

  public Sessao(
      String prefixo,
      long perfil,
      long titulo,
      Long episodio,
      double duracaoEmSegundos,
      Instant comecouEm) {
    this.prefixo = Objects.requireNonNull(prefixo, "prefixo");
    this.perfil = perfil;
    this.titulo = titulo;
    this.episodio = episodio;
    this.duracaoEmSegundos = duracaoEmSegundos;
    this.comecouEm = Objects.requireNonNull(comecouEm, "comecouEm");
  }

  /**
   * Os eventos de assistir do começo até um ponto.
   *
   * @param ateSegundo onde a sessão parou
   * @param terminou se o último evento é {@code FIM} ou {@code PAUSA}
   */
  public List<Evento> ate(double ateSegundo, boolean terminou) {
    if (ateSegundo < 0) {
      throw new IllegalArgumentException("ateSegundo negativo: " + ateSegundo);
    }

    List<Evento> eventos = new ArrayList<>();
    long passo = BATIMENTO.toSeconds();
    int n = 0;

    eventos.add(evento(n++, Evento.Tipo.INICIO, 0, comecouEm));

    for (double segundo = passo; segundo < ateSegundo; segundo += passo) {
      eventos.add(
          evento(n++, Evento.Tipo.PROGRESSO, segundo, comecouEm.plusSeconds((long) segundo)));
    }

    eventos.add(
        evento(
            n,
            terminou ? Evento.Tipo.FIM : Evento.Tipo.PAUSA,
            ateSegundo,
            comecouEm.plusSeconds((long) ateSegundo)));

    return eventos;
  }

  /** Os eventos de assistir a obra inteira. */
  public List<Evento> inteira() {
    return ate(duracaoEmSegundos, true);
  }

  private Evento evento(int n, Evento.Tipo tipo, double segundo, Instant quando) {
    return new Evento(
        prefixo + "-" + n, perfil, titulo, episodio, tipo, segundo, duracaoEmSegundos, quando);
  }
}
