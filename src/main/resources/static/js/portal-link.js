'use strict';

/* Link "← Portal IFSISTEM": a URL vem de GET /config (PORTAL_URL). Sem URL configurada, fica escondido. */
(function () {
    const link = document.querySelector('.voltar-portal');
    if (!link) return;
    fetch('config').then(r => r.ok ? r.json() : {}).then(c => {
        const url = (c.portalUrl || '').trim();
        if (url && url !== '#') { link.href = url; link.hidden = false; }
    }).catch(() => { /* mantem escondido */ });
})();
