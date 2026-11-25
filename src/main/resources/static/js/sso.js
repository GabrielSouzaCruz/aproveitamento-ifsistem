'use strict';

/* Login unico (ifsistem-auth): le GET /config, carrega ${authUrl}/ifsistem-sso.js e configura IfsistemSSO.
 * Todas as paginas esperam `ssoPronto` antes de usar IfsistemSSO. */
const ssoPronto = (async () => {
    const resp = await fetch('config');
    if (!resp.ok) throw new Error('Não foi possível ler a configuração do sistema.');
    const c = await resp.json();
    const authUrl = String(c.authUrl || '').replace(/\/+$/, '');
    window.IFSISTEM_AUTH_URL = authUrl; // js/usuarios.js chama a API /usuarios direto neste endereço
    await new Promise((ok, falha) => {
        const s = document.createElement('script');
        s.src = authUrl + '/ifsistem-sso.js';
        s.onload = ok;
        s.onerror = () => falha(new Error('O login do IFSISTEM não respondeu. Tente novamente em instantes.'));
        document.head.appendChild(s);
    });
    // `const IfsistemSSO` de um script classico e visivel aos demais scripts, mas nao vira window.IfsistemSSO.
    IfsistemSSO.init({
        authUrl,
        clientId: c.clientId,
        redirectUri: location.origin + '/callback.html',
        postLogoutRedirectUri: location.origin + '/'
    });
    return c;
})();

/* Rota pretendida (hash de aproveitamento.html) sobrevive ao ciclo login -> callback.html, que perde o hash. */
const CHAVE_ROTA = 'aproveitamento.rota';

/** Guarda o hash atual (se for uma rota alem da inicial) e vai ao login do auth. */
function entrar() {
    if (/^#\/.+/.test(location.hash)) sessionStorage.setItem(CHAVE_ROTA, location.hash);
    else sessionStorage.removeItem(CHAVE_ROTA);
    return IfsistemSSO.login();
}

/** Devolve (e esquece) a rota guardada antes do login; '' se nao houver ou se nao parecer uma rota. */
function rotaPretendida() {
    const h = sessionStorage.getItem(CHAVE_ROTA) || '';
    sessionStorage.removeItem(CHAVE_ROTA);
    return /^#\/[\w\/-]*$/.test(h) ? h : '';
}
