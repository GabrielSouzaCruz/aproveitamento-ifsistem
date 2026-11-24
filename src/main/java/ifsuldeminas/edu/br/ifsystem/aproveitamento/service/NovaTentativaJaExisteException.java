package ifsuldeminas.edu.br.ifsystem.aproveitamento.service;

import java.util.UUID;

/** Ja existe uma nova tentativa para a solicitacao indeferida (409); carrega o id dela. */
public class NovaTentativaJaExisteException extends RuntimeException {
    private final UUID novaTentativaId;

    public NovaTentativaJaExisteException(UUID novaTentativaId) {
        super("Já existe uma nova tentativa para esta solicitação");
        this.novaTentativaId = novaTentativaId;
    }

    public UUID getNovaTentativaId() {
        return novaTentativaId;
    }
}
