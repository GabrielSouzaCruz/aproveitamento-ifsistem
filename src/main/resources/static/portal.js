'use strict';

/* Portal publico: monta os cards a partir de modulos.json. Modulo inativo vira card sem link. */

function el(tag, classe, texto) {
    const e = document.createElement(tag);
    if (classe) e.className = classe;
    if (texto != null) e.textContent = texto;
    return e;
}

function card(m) {
    const c = el('article', 'modulo' + (m.ativo ? '' : ' inativo'));
    c.appendChild(el('span', 'modulo-icone', m.icone || ''));
    c.appendChild(el('h2', null, m.nome));
    c.appendChild(el('p', 'muted', m.descricao));
    if (m.ativo && m.url) {
        const a = el('a', 'btn', 'Acessar');
        a.href = m.url; // externa (http...) ou interna: sempre na mesma aba
        c.appendChild(a);
    } else {
        c.appendChild(el('span', 'selo-breve', 'Em breve'));
    }
    return c;
}

fetch('modulos.json')
    .then(r => r.json())
    .then(lista => document.getElementById('modulos').replaceChildren(...lista.map(card)))
    .catch(() => {
        document.getElementById('modulos').textContent = 'Não foi possível carregar os módulos.';
    });
