package br.com.conde.eventos;

/**
 * Título e episódio juntos.
 *
 * <p>Num filme o episódio é {@code null}. Tratar isso como um id de episódio
 * zero pareceria inofensivo e faria todos os filmes do catálogo colidirem num
 * balde só.
 *
 * @param titulo o id do título
 * @param episodio o id do episódio, ou {@code null} num filme
 */
public record ChaveDaObra(long titulo, Long episodio) {

  /** Se é um episódio de série. */
  public boolean ehEpisodio() {
    return episodio != null;
  }

  @Override
  public String toString() {
    return episodio == null ? "título " + titulo : "título " + titulo + ", episódio " + episodio;
  }
}
