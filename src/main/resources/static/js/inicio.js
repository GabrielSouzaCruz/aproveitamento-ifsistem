'use strict';

/* Ponto de entrada: carregado por ultimo, depois que todas as telas foram definidas.
 * Sem sessao no sistema, vai ao login do auth (que entra direto se a sessao de la ainda estiver ativa). */
ssoPronto
    .then(async () => {
        if (!(await IfsistemSSO.getToken())) return IfsistemSSO.login();
        return rotear();
    })
    .catch(e => mostrarAviso(e.message || 'Falha ao iniciar a sessão.'));
