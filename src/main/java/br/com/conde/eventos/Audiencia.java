package br.com.conde.eventos;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Quem foi assistido, quando — o "top 10 da semana".
 *
 * <p>A contagem é por <b>balde de hora</b>, e a escolha do balde é a decisão
 * central deste arquivo.
 *
 * <p>Guardar cada evento com seu instante e filtrar por data na hora da
 * pergunta funciona — até o catálogo crescer. Aí são milhões de linhas
 * varridas para desenhar uma fileira que muda de hora em hora.
 *
 * <p>Guardar um contador único por título é barato e não responde à pergunta:
 * sem instante não há como tirar da conta o que saiu da janela, e o "top da
 * semana" vira "top desde sempre" — uma fileira que nunca muda, e que nenhum
 * lançamento consegue alcançar.
 *
 * <p>O balde de hora fica no meio: 168 baldes cobrem uma semana, a janela anda
 * jogando fora os baldes vencidos, e a resolução perdida — saber que algo foi
 * assistido às 14h e não às 14h37 — não muda nada numa fileira semanal.
 *
 * <p>A audiência conta <b>perfis distintos por balde</b>, não eventos: um
 * batimento a cada trinta segundos geraria cento e vinte "visualizações" por
 * hora de quem assistiu uma vez.
 */
public final class Audiencia {

  /** O tamanho do balde. */
  public static final ChronoUnit BALDE = ChronoUnit.HOURS;

  /** Uma posição no ranque. */
  public record Posicao(long titulo, int espectadores) {}

  private final NavigableMap<Instant, Map<Long, Set<Long>>> baldes = new TreeMap<>();

  /** Registra que um perfil assistiu um título. */
  public void contar(Evento evento) {
    Objects.requireNonNull(evento, "evento");

    // Um salto é navegação, não audiência: quem arrasta a barra procurando uma
    // cena não está assistindo, e contar isso premiaria o que é difícil de
    // achar dentro do próprio vídeo.
    if (evento.tipo() == Evento.Tipo.SALTO) {
      return;
    }

    Instant balde = evento.ocorridoEm().truncatedTo(BALDE);

    baldes
        .computeIfAbsent(balde, chave -> new HashMap<>())
        .computeIfAbsent(evento.titulo(), chave -> new HashSet<>())
        .add(evento.perfil());
  }

  /**
   * Os mais assistidos numa janela que termina em {@code agora}.
   *
   * <p>A janela é <b>fechada no fim e aberta no começo</b>: inclui o balde de
   * agora e exclui o balde que completou a janela. Sem essa regra, uma consulta
   * feita às 14h00 e outra às 14h01 olhariam conjuntos diferentes de baldes e
   * o ranque saltaria na virada da hora.
   */
  public List<Posicao> top(int quantos, Instant agora, Duration janela) {
    Objects.requireNonNull(agora, "agora");
    Objects.requireNonNull(janela, "janela");

    if (quantos <= 0) {
      return List.of();
    }

    Instant fim = agora.truncatedTo(BALDE);
    Instant comeco = fim.minus(janela);

    Map<Long, Set<Long>> somados = new HashMap<>();

    for (Map<Long, Set<Long>> balde : baldes.subMap(comeco, false, fim, true).values()) {
      for (Map.Entry<Long, Set<Long>> entrada : balde.entrySet()) {
        somados.computeIfAbsent(entrada.getKey(), chave -> new HashSet<>()).addAll(entrada.getValue());
      }
    }

    List<Posicao> ranque = new ArrayList<>(somados.size());

    for (Map.Entry<Long, Set<Long>> entrada : somados.entrySet()) {
      ranque.add(new Posicao(entrada.getKey(), entrada.getValue().size()));
    }

    // Desempate pelo id: sem ele, dois títulos com a mesma audiência trocariam
    // de lugar a cada consulta e a fileira pareceria se mexer sozinha.
    ranque.sort(
        Comparator.comparingInt(Posicao::espectadores).reversed().thenComparingLong(Posicao::titulo));

    return ranque.subList(0, Math.min(quantos, ranque.size()));
  }

  /**
   * Joga fora os baldes anteriores ao corte.
   *
   * <p>Sem isto a estrutura cresce para sempre. Com isto, ela tem tamanho
   * máximo conhecido: um balde por hora da janela mais longa que se pretende
   * consultar.
   */
  public int esquecerAntesDe(Instant corte) {
    Objects.requireNonNull(corte, "corte");

    NavigableMap<Instant, Map<Long, Set<Long>>> velhos =
        baldes.headMap(corte.truncatedTo(BALDE), false);
    int quantos = velhos.size();

    velhos.clear();

    return quantos;
  }

  /** Quantos baldes estão guardados. */
  public int baldes() {
    return baldes.size();
  }

  /** O balde mais antigo guardado, ou {@code null} se não há nenhum. */
  public Instant maisAntigo() {
    return baldes.isEmpty() ? null : baldes.firstKey();
  }
}
