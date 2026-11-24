/* Modal próprio do sistema (substitui os diálogos nativos do navegador). Ambas as funções retornam Promise. */
(function () {
    function esc(v) {
        return String(v ?? '').replace(/[&<>"']/g, c =>
            ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
    }

    function abrir({ titulo, mensagem, rotulo, placeholder, obrigatorio, maxLength, textoOk, perigo, comTexto }) {
        return new Promise(resolve => {
            const anterior = document.activeElement;
            const fundo = document.createElement('div');
            fundo.className = 'modal-fundo';
            const idTitulo = 'modal-t-' + Math.random().toString(36).slice(2);
            fundo.innerHTML = `
                <div class="modal" role="dialog" aria-modal="true" aria-labelledby="${idTitulo}">
                    <h2 id="${idTitulo}">${esc(titulo)}</h2>
                    ${mensagem ? `<p class="modal-msg">${esc(mensagem)}</p>` : ''}
                    ${comTexto ? `
                        <label class="modal-rotulo" for="${idTitulo}-campo">${esc(rotulo || '')}${obrigatorio ? '' : ' (opcional)'}</label>
                        <textarea id="${idTitulo}-campo" rows="4" maxlength="${maxLength}" placeholder="${esc(placeholder || '')}"></textarea>
                        <div class="modal-rodape-campo">
                            <span class="modal-erro" hidden>Este campo é obrigatório.</span>
                            <span class="modal-contador"></span>
                        </div>` : ''}
                    <div class="modal-acoes">
                        <button type="button" class="btn btn-secundario" data-modal="cancelar">Cancelar</button>
                        <button type="button" class="btn ${perigo ? 'btn-perigo-cheio' : 'btn-primario'}" data-modal="ok">${esc(textoOk || 'Confirmar')}</button>
                    </div>
                </div>`;
            document.body.appendChild(fundo);
            const campo = fundo.querySelector('textarea');
            const ok = fundo.querySelector('[data-modal="ok"]');
            const erroEl = fundo.querySelector('.modal-erro');
            const contador = fundo.querySelector('.modal-contador');
            let tocado = false;

            const valor = () => (campo ? campo.value.trim() : '');
            function atualizar() {
                if (!campo) return;
                contador.textContent = `${campo.value.length}/${maxLength}`;
                const invalido = obrigatorio && !valor();
                ok.disabled = invalido;
                erroEl.hidden = !(invalido && tocado);
            }
            function fechar(resultado) {
                document.removeEventListener('keydown', teclas, true);
                fundo.remove();
                if (anterior && anterior.focus) anterior.focus();
                resolve(resultado);
            }
            function confirmar() {
                if (ok.disabled) return;
                fechar(comTexto ? valor() : true);
            }
            function teclas(e) {
                if (e.key === 'Escape') { e.preventDefault(); fechar(comTexto ? null : false); }
                else if (e.key === 'Enter' && !e.isComposing) {
                    if (e.target && e.target.dataset && e.target.dataset.modal === 'cancelar') return;
                    if (campo ? e.ctrlKey || e.metaKey : true) { e.preventDefault(); confirmar(); }
                } else if (e.key === 'Tab') {
                    const foco = [...fundo.querySelectorAll('textarea, button:not(:disabled)')];
                    if (!foco.length) return;
                    const i = foco.indexOf(document.activeElement);
                    if (e.shiftKey && i <= 0) { e.preventDefault(); foco[foco.length - 1].focus(); }
                    else if (!e.shiftKey && i === foco.length - 1) { e.preventDefault(); foco[0].focus(); }
                }
            }
            document.addEventListener('keydown', teclas, true);
            fundo.addEventListener('mousedown', e => { if (e.target === fundo) fechar(comTexto ? null : false); });
            fundo.querySelector('[data-modal="cancelar"]').onclick = () => fechar(comTexto ? null : false);
            ok.onclick = confirmar;
            if (campo) campo.addEventListener('input', () => { tocado = true; atualizar(); });
            atualizar();
            (campo || ok).focus();
        });
    }

    /** Resolve true (confirmou) ou false (cancelou/Esc/clique fora). */
    window.modalConfirmar = ({ titulo = 'Confirmar', mensagem = '', textoOk = 'Confirmar', perigo = false } = {}) =>
        abrir({ titulo, mensagem, textoOk, perigo, comTexto: false });

    /** Resolve o texto digitado (string, possivelmente vazia se opcional) ou null se cancelou. */
    window.modalTexto = ({ titulo = '', mensagem = '', rotulo = '', placeholder = '', obrigatorio = false,
                           maxLength = 500, textoOk = 'Confirmar', perigo = false } = {}) =>
        abrir({ titulo, mensagem, rotulo, placeholder, obrigatorio, maxLength, textoOk, perigo, comTexto: true });
})();
