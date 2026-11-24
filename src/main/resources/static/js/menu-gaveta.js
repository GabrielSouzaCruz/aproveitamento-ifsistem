/* Menu lateral como gaveta em telas <= 1024px: abre pelo hamburguer; fecha ao clicar fora, com Esc ou ao escolher um item. */
(function () {
    const botao = document.getElementById('btn-menu');
    const overlay = document.getElementById('overlay');
    const lateral = document.getElementById('lateral');
    if (!botao || !overlay || !lateral) return;
    const largaDemais = window.matchMedia('(min-width: 1025px)');

    function aberta() { return document.body.classList.contains('menu-aberto'); }

    function abrir() {
        document.body.classList.add('menu-aberto');
        overlay.hidden = false;
        botao.setAttribute('aria-expanded', 'true');
        botao.setAttribute('aria-label', 'Fechar menu');
        const primeiro = lateral.querySelector('.menu a');
        if (primeiro) primeiro.focus({ preventScroll: true });
    }

    function fechar(devolverFoco) {
        if (!aberta()) return;
        document.body.classList.remove('menu-aberto');
        overlay.hidden = true;
        botao.setAttribute('aria-expanded', 'false');
        botao.setAttribute('aria-label', 'Abrir menu');
        if (devolverFoco) botao.focus({ preventScroll: true });
    }

    botao.addEventListener('click', () => (aberta() ? fechar(true) : abrir()));
    overlay.addEventListener('click', () => fechar(true));
    document.addEventListener('keydown', e => { if (e.key === 'Escape' && aberta()) fechar(true); });
    // Itens do menu sao desenhados depois (delegacao); sair tambem fecha.
    lateral.addEventListener('click', e => { if (e.target.closest('.menu a, .marca, #btn-sair, .voltar-portal')) fechar(false); });
    window.addEventListener('hashchange', () => fechar(false));
    const aoRedimensionar = () => { if (largaDemais.matches) fechar(false); };
    if (largaDemais.addEventListener) largaDemais.addEventListener('change', aoRedimensionar);
})();
