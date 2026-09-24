package br.com.conde.eventos;

/**
 * Perfil, título e episódio: a chave de "continuar assistindo".
 *
 * <p>O perfil faz parte da chave porque duas pessoas na mesma casa assistem a
 * mesma série em pontos diferentes, e cada uma precisa retomar de onde parou.
 * É o motivo de o perfil existir num serviço de streaming.
 *
 * @param perfil quem assistia
 * @param titulo o id do título
 * @param episodio o id do episódio, ou {@code null} num filme
 */
public record ChaveDoProgresso(long perfil, long titulo, Long episodio) {

  /** A obra, sem o perfil. */
  public ChaveDaObra obra() {
    return new ChaveDaObra(titulo, episodio);
  }
}
