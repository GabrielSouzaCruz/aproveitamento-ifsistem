'use strict';

/* Detalhe para professor/secretaria: ementa de origem ao lado da ementa de destino. */

function blocoEmentas(s) {
    const totalOrigem = s.disciplinas.reduce((soma, d) => soma + (Number(d.cargaHoraria) || 0), 0);
    const destino = s.ementaDestino
        ? `<p class="ementa">${esc(s.ementaDestino)}</p>`
        : '<p class="muted">Solicitação anterior ao catálogo: não há ementa de destino cadastrada.</p>';
    return `
        <section class="card">
            <div class="card-cab">
                <h3>Comparação de ementas</h3>
                <span class="muted pequeno">Carga cursada: <strong>${totalOrigem} h</strong> · Carga da disciplina destino: <strong>${esc(s.cargaHorariaDestino)} h</strong></span>
            </div>
            <div class="comparacao">
                <div class="ementa-coluna">
                    <p class="sobretitulo">Ementa de origem</p>
                    ${s.disciplinas.map(d => `
                        <article class="ementa-bloco">
                            <strong>${esc(d.nome)}</strong>
                            <span class="muted pequeno">${esc(d.instituicao)} · ${esc(d.cargaHoraria)} h</span>
                            <p class="ementa">${esc(d.ementa)}</p>
                        </article>`).join('')}
                </div>
                <div class="ementa-coluna">
                    <p class="sobretitulo">Ementa de destino</p>
                    <article class="ementa-bloco destino">
                        <strong>${esc(s.disciplinaDestino)}</strong>
                        <span class="muted pequeno">${esc(s.curso)} · ${esc(s.cargaHorariaDestino)} h</span>
                        ${destino}
                    </article>
                </div>
            </div>
        </section>`;
}
