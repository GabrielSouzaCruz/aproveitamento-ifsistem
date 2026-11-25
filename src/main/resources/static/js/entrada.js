'use strict';

/* Entrada ("/", index.html, login.html): sem tela de login propria. Com token valido vai direto ao painel;
 * sem token chama o login do auth na hora. Enquanto isso a pagina mostra so logo e spinner. */
(async () => {
    const aviso = setTimeout(() => {
        const t = document.getElementById('conectando');
        if (t) t.hidden = false;
    }, 5000);
    try {
        await ssoPronto;
        if (await IfsistemSSO.getToken()) { location.replace('aproveitamento.html'); return; }
        sessionStorage.removeItem(CHAVE_ROTA);
        await IfsistemSSO.login();
    } catch (e) {
        clearTimeout(aviso);
        const m = document.getElementById('erro');
        m.textContent = e.message || 'Não foi possível conectar ao IFSISTEM.';
        m.hidden = false;
        document.getElementById('carregando').hidden = true;
        document.getElementById('conectando').hidden = true;
        document.getElementById('de-novo').hidden = false;
    }
})();
