package br.com.conde.eventos;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Recebe eventos de reprodução e responde duas perguntas: <b>onde cada pessoa
 * parou</b> e <b>o que está sendo assistido</b>.
 *
 * <p>Tudo aqui gira em torno de um fato do mundo real: <b>os eventos chegam
 * repetidos e fora de ordem</b>. Não é caso excepcional, é o caso comum.
 *
 * <dl>
 *   <dt>Repetidos</dt>
 *   <dd>A entrega é ao menos uma vez. O player reenvia o que não teve
 *       confirmação, o aplicativo reaberto despeja a fila local de novo, a rede
 *       duplica. Contar duas vezes o mesmo evento infla a audiência de quem tem
 *       conexão ruim — que é exatamente quem reenvia mais.</dd>
 *
 *   <dt>Fora de ordem</dt>
 *   <dd>O celular ficou sem sinal no metrô e mandou tudo junto ao emergir; o
 *       aparelho mais rápido chegou primeiro. Um evento de "estou em 5:00" pode
 *       chegar <b>depois</b> de um "estou em 40:00". Gravar o último que chegou
 *       faz a pessoa voltar para o começo do episódio sem entender por quê. É
 *       a falha mais irritante possível num serviço de streaming, porque
 *       parece que o sistema esqueceu.</dd>
 * </dl>
 *
 * <p>As duas defesas são pequenas e ficam em {@link #receber(Evento)}: um
 * conjunto de ids já vistos e uma comparação por {@link Evento#ocorridoEm()}
 * antes de gravar.
 */
public final class Telemetria {

  /** Quanto tempo um id fica guardado para detectar repetição. */
  public static final Duration JANELA_DE_REPETICAO = Duration.ofHours(6);

  private final Map<ChaveDoProgresso, Progresso> progressos = new HashMap<>();
  private final Map<String, Instant> idsVistos = new LinkedHashMap<>();
  private final Audiencia audiencia;
  private final Duration janelaDeRepeticao;

  private Instant maiorInstante = Instant.EPOCH;
  private long recebidos;
  private long repetidos;
  private long atrasadosDescartados;

  public Telemetria() {
    this(new Audiencia(), JANELA_DE_REPETICAO);
  }

  public Telemetria(Audiencia audiencia, Duration janelaDeRepeticao) {
    this.audiencia = Objects.requireNonNull(audiencia, "audiencia");
    this.janelaDeRepeticao = Objects.requireNonNull(janelaDeRepeticao, "janelaDeRepeticao");
  }

  /**
   * Recebe um evento.
   *
   * @return {@code true} se o evento mudou alguma coisa; {@code false} se era
   *     repetido ou velho demais para importar
   */
  public boolean receber(Evento evento) {
    Objects.requireNonNull(evento, "evento");

    recebidos++;

    // 1. Repetição. O id é o que diz que é o mesmo evento, e não um evento
    // parecido: dois batimentos podem ter o mesmo perfil, título, segundo e
    // até instante, e ainda assim serem dois.
    if (idsVistos.containsKey(evento.id())) {
      repetidos++;

      return false;
    }

    idsVistos.put(evento.id(), evento.ocorridoEm());

    if (evento.ocorridoEm().isAfter(maiorInstante)) {
      maiorInstante = evento.ocorridoEm();
    }

    limparIdsAntigos();

    // A audiência conta o evento mesmo fora de ordem: para "o que está sendo
    // assistido" o que importa é que aconteceu, não a ordem em que chegou.
    audiencia.contar(evento);

    // 2. Fora de ordem. O progresso é o estado "onde a pessoa está", e estado
    // não pode ser sobrescrito por notícia velha.
    return aplicarAoProgresso(evento);
  }

  /** Recebe vários eventos e devolve quantos mudaram alguma coisa. */
  public int receberTodos(Iterable<Evento> eventos) {
    int mudaram = 0;

    for (Evento evento : eventos) {
      if (receber(evento)) {
        mudaram++;
      }
    }

    return mudaram;
  }

  private boolean aplicarAoProgresso(Evento evento) {
    ChaveDoProgresso chave = evento.chave();
    Progresso atual = progressos.get(chave);

    if (atual != null && !evento.ocorridoEm().isAfter(atual.atualizadoEm())) {
      // Chegou atrasado: o que já está gravado é mais novo. Gravar assim mesmo
      // é o que faz a pessoa voltar ao começo do episódio.
      //
      // O empate também é descartado, e de propósito: dois eventos no mesmo
      // instante não têm ordem definida, e "o último que chegou" seria decidir
      // pela rede, que é o que este método existe para não fazer.
      atrasadosDescartados++;

      return false;
    }

    // "Concluído" sai do evento mais recente, e só dele.
    //
    // A primeira versão também herdava o `concluido` do estado anterior — uma
    // conclusão, uma vez alcançada, ficava para sempre. Parecia razoável e
    // estava errado duas vezes:
    //
    // 1. Fazia o resultado depender da ordem de chegada. Quem terminou na
    //    segunda e reassistiu 20% na quarta terminava com `concluido=true` se
    //    os eventos chegassem em ordem, e `false` se a quarta chegasse antes —
    //    porque aí a segunda era descartada por atraso e nunca contribuía.
    // 2. A resposta "pegajosa" é a pior das duas. Quem reassiste está
    //    assistindo, e a fileira "continuar assistindo" precisa mostrar isso;
    //    a conclusão de semana passada não pode esconder a sessão de hoje.
    //
    // Derivar só do evento mais recente conserta as duas de uma vez.
    boolean concluido =
        evento.tipo() == Evento.Tipo.FIM || evento.fracao() >= Progresso.FRACAO_DE_CONCLUSAO;

    progressos.put(
        chave,
        new Progresso(evento.segundo(), evento.duracaoEmSegundos(), evento.ocorridoEm(), concluido));

    return true;
  }

  private void limparIdsAntigos() {
    Instant corte = maiorInstante.minus(janelaDeRepeticao);

    // O LinkedHashMap preserva a ordem de inserção, mas os eventos chegam fora
    // de ordem — então a ordem de inserção não é a ordem dos instantes, e não
    // dá para parar no primeiro que sobrevive. A varredura é completa.
    idsVistos.entrySet().removeIf(entrada -> entrada.getValue().isBefore(corte));
  }

  /** Onde um perfil parou numa obra. */
  public Optional<Progresso> progresso(long perfil, long titulo, Long episodio) {
    return Optional.ofNullable(progressos.get(new ChaveDoProgresso(perfil, titulo, episodio)));
  }

  /**
   * A fileira "continuar assistindo" de um perfil, do mais recente ao mais
   * antigo.
   */
  public List<ChaveDaObra> continuarAssistindo(long perfil) {
    List<Map.Entry<ChaveDoProgresso, Progresso>> emAndamento = new ArrayList<>();

    for (Map.Entry<ChaveDoProgresso, Progresso> entrada : progressos.entrySet()) {
      if (entrada.getKey().perfil() == perfil && entrada.getValue().emAndamento()) {
        emAndamento.add(entrada);
      }
    }

    emAndamento.sort(
        Comparator.comparing(
                (Map.Entry<ChaveDoProgresso, Progresso> e) -> e.getValue().atualizadoEm())
            .reversed()
            // Desempate estável: sem ele, duas obras paradas no mesmo instante
            // trocariam de lugar entre execuções e a fileira pareceria
            // embaralhar sozinha.
            .thenComparingLong(e -> e.getKey().titulo())
            .thenComparing(e -> e.getKey().episodio(), Comparator.nullsFirst(Long::compare)));

    List<ChaveDaObra> fileira = new ArrayList<>(emAndamento.size());

    for (Map.Entry<ChaveDoProgresso, Progresso> entrada : emAndamento) {
      fileira.add(entrada.getKey().obra());
    }

    return fileira;
  }

  /** Os títulos mais assistidos numa janela que termina agora. */
  public List<Audiencia.Posicao> top(int quantos, Instant agora, Duration janela) {
    return audiencia.top(quantos, agora, janela);
  }

  /** Os títulos mais assistidos nos últimos sete dias. */
  public List<Audiencia.Posicao> topDaSemana(int quantos, Instant agora) {
    return audiencia.top(quantos, agora, Duration.ofDays(7));
  }

  /** Os perfis que este acumulador conhece. */
  public Set<Long> perfis() {
    Set<Long> encontrados = new LinkedHashSet<>();

    for (ChaveDoProgresso chave : progressos.keySet()) {
      encontrados.add(chave.perfil());
    }

    return encontrados;
  }

  /** Quantos eventos entraram, ao todo. */
  public long recebidos() {
    return recebidos;
  }

  /** Quantos foram descartados por já terem sido vistos. */
  public long repetidos() {
    return repetidos;
  }

  /**
   * Quantos chegaram atrasados demais para mudar o progresso.
   *
   * <p>Este número não é detalhe de implementação: se ele disparar, alguma
   * frota de aparelhos está com o relógio errado, e vale saber antes que as
   * reclamações cheguem.
   */
  public long atrasadosDescartados() {
    return atrasadosDescartados;
  }

  /** Quantos ids estão guardados no momento. */
  public int idsGuardados() {
    return idsVistos.size();
  }

  /** A audiência, para quem quiser perguntar direto. */
  public Audiencia audiencia() {
    return audiencia;
  }
}
