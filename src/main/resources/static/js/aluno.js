'use strict';

/* Telas do aluno: cursadas no detalhe e "Nova solicitacao" (curso e disciplina do catalogo). */

/** Disciplinas cursadas (com ementa) no detalhe do aluno; o aluno nao ve parecer da IA. */
function blocoCursadasAluno(s) {
    return `
        <h3 style="margin-top:1.5rem">Disciplinas cursadas <span class="contador">${s.disciplinas.length}</span></h3>
        ${s.disciplinas.map(d => `
            <details class="cursada">
                <summary><strong>${esc(d.nome)}</strong> · ${esc(d.instituicao)} · ${esc(d.cargaHoraria)} h</summary>
                <p class="ementa">${esc(d.ementa)}</p>
            </details>`).join('')}`;
}

function linhaDisciplina() {
    return `
        <fieldset class="linha-disc">
            <div class="campos">
                <div><label>Disciplina cursada<input name="nome" required maxlength="255" placeholder="Ex.: Banco de Dados I"></label></div>
                <div><label>Instituição<input name="instituicao" required maxlength="255" placeholder="Ex.: UNIFAL"></label></div>
                <div><label>Carga (h)<input name="cargaHoraria" type="number" min="1" required></label></div>
                <div><button type="button" class="btn btn-perigo btn-mini remover-disc" title="Remover disciplina" aria-label="Remover disciplina">×</button></div>
            </div>
            <label>Ementa da disciplina cursada
                <textarea name="ementa" rows="4" required placeholder="Cole aqui os conteúdos programáticos da disciplina cursada"></textarea></label>
        </fieldset>`;
}

/** Nova solicitacao; com `editarId`, reaproveita o formulario para editar rascunho/pendencia (PUT). */
async function telaNova(editarId) {
    const edicao = !!editarId;
    titulo(edicao ? 'Editar solicitação' : 'Nova solicitação');
    app.innerHTML = '<p class="muted carregando">Carregando catálogo</p>';
    const [cursos, atual] = await Promise.all([http('/cursos'), edicao ? api('/' + editarId) : Promise.resolve(null)]);
    if (atual && !STATUS_ANEXO_EDITAVEL.includes(atual.status))
        return navegar('#/s/' + editarId, 'Esta solicitação não pode mais ser editada.');

    app.innerHTML = `
        <a class="voltar" href="${edicao ? '#/s/' + esc(editarId) : '#/'}">${ICONES.voltar} Voltar</a>
        ${atual ? blocoOrigem(atual, usuarioAtual()) : ''}
        <form id="form-nova" class="card">
            <h3>Disciplina que deseja aproveitar</h3>
            <div class="campos-2">
                <div class="campo"><label for="curso">Curso</label>
                    <select id="curso" required>
                        <option value="">Selecione o curso</option>
                        ${cursos.map(c => `<option value="${esc(c.id)}">${esc(c.nome)}</option>`).join('')}
                    </select></div>
                <div class="campo"><label for="disciplinaDestino">Disciplina destino</label>
                    <select id="disciplinaDestino" required disabled><option value="">Selecione o curso primeiro</option></select></div>
            </div>
            <div id="destino-info" class="ementa-destino" hidden></div>

            <h3 style="margin-top:1rem">Disciplinas já cursadas</h3>
            <div id="disciplinas">${linhaDisciplina()}</div>
            <button type="button" class="btn btn-sec btn-mini" id="add-disc">+ Adicionar disciplina</button>

            ${edicao ? '' : `<h3 style="margin-top:1.5rem">Documento comprobatório</h3>
            <div class="campo">
                <input type="file" id="arquivo" accept="application/pdf,.pdf" aria-label="Arquivo PDF (histórico ou ementa)">
                <p class="muted pequeno" style="margin:.3rem 0 0">Opcional agora: PDF de até 5 MB. Você também pode anexar depois, enquanto for rascunho.</p>
            </div>`}

            <div class="acoes" style="margin-top:1.25rem">
                ${edicao
                    ? '<button type="submit" class="btn" data-modo="rascunho">Salvar alterações</button>'
                    : '<button type="submit" class="btn btn-sec" data-modo="rascunho">Salvar rascunho</button><button type="submit" class="btn" data-modo="enviar">Salvar e enviar</button>'}
                <a class="btn btn-sec" href="${edicao ? '#/s/' + esc(editarId) : '#/'}">Cancelar</a>
            </div>
            <p class="muted pequeno">O rascunho pode ser conferido, receber anexos e ser enviado a qualquer momento em "Minhas solicitações".</p>
        </form>`;

    const form = document.getElementById('form-nova');
    const selCurso = document.getElementById('curso');
    const selDisc = document.getElementById('disciplinaDestino');
    const info = document.getElementById('destino-info');
    let disciplinas = [];

    selCurso.onchange = async () => {
        info.hidden = true;
        selDisc.innerHTML = '<option value="">Carregando…</option>';
        selDisc.disabled = true;
        if (!selCurso.value) { selDisc.innerHTML = '<option value="">Selecione o curso primeiro</option>'; return; }
        try {
            disciplinas = await http(`/cursos/${selCurso.value}/disciplinas`);
        } catch (e) {
            disciplinas = [];
            erro(e);
        }
        selDisc.innerHTML = disciplinas.length
            ? '<option value="">Selecione a disciplina</option>' + disciplinas.map(d =>
                `<option value="${esc(d.id)}">${esc(d.nome)} (${esc(d.cargaHoraria)} h)</option>`).join('')
            : '<option value="">Este curso não tem disciplinas disponíveis</option>';
        selDisc.disabled = disciplinas.length === 0;
    };
    selDisc.onchange = () => {
        const d = disciplinas.find(x => x.id === selDisc.value);
        info.hidden = !d;
        if (d) info.innerHTML = `<strong>Ementa de destino · ${esc(d.nome)} · ${esc(d.cargaHoraria)} h</strong><p class="ementa">${esc(d.ementa)}</p>`;
    };

    const lista = document.getElementById('disciplinas');
    lista.onclick = e => {
        const b = e.target.closest('.remover-disc');
        if (b && lista.children.length > 1) b.closest('.linha-disc').remove();
    };
    document.getElementById('add-disc').onclick = () => lista.insertAdjacentHTML('beforeend', linhaDisciplina());

    if (atual) {
        // Pre-preenche destino (curso e disciplina) e as disciplinas cursadas ja cadastradas.
        lista.innerHTML = atual.disciplinas.map(() => linhaDisciplina()).join('') || linhaDisciplina();
        atual.disciplinas.forEach((d, i) => {
            const l = lista.children[i];
            l.querySelector('[name=nome]').value = d.nome;
            l.querySelector('[name=instituicao]').value = d.instituicao;
            l.querySelector('[name=cargaHoraria]').value = d.cargaHoraria;
            l.querySelector('[name=ementa]').value = d.ementa || '';
        });
        if (atual.cursoId) {
            selCurso.value = atual.cursoId;
            await selCurso.onchange();
            const destino = disciplinas.find(x => x.nome === atual.disciplinaDestino);
            if (destino) { selDisc.value = destino.id; selDisc.onchange(); }
        }
    }

    form.onsubmit = async e => {
        e.preventDefault();
        const enviar = e.submitter && e.submitter.dataset.modo === 'enviar';
        const campoArquivo = document.getElementById('arquivo');
        const arquivo = campoArquivo ? campoArquivo.files[0] : null;
        if (arquivo && arquivo.size > 5 * 1024 * 1024) return mostrarAviso('Arquivo maior que o limite de 5 MB.');
        const dados = {
            ...(edicao ? {} : { alunoId: usuarioAtual().id }),
            disciplinaDestinoId: selDisc.value,
            disciplinas: [...lista.querySelectorAll('.linha-disc')].map(l => ({
                nome: l.querySelector('[name=nome]').value.trim(),
                instituicao: l.querySelector('[name=instituicao]').value.trim(),
                cargaHoraria: Number(l.querySelector('[name=cargaHoraria]').value),
                ementa: l.querySelector('[name=ementa]').value.trim()
            }))
        };
        const botoes = form.querySelectorAll('[type=submit]');
        botoes.forEach(b => b.disabled = true);
        if (edicao) {
            try {
                await api('/' + editarId, { method: 'PUT', json: dados, ctx: 'solicitacao' });
                return navegar('#/s/' + editarId, 'Solicitação atualizada.', true);
            } catch (err) {
                erro(err);
                if (err.status === 409) return navegar('#/s/' + editarId);
                botoes.forEach(b => b.disabled = false);
                return;
            }
        }
        let criada;
        try {
            criada = await api('', { method: 'POST', json: dados, ctx: 'solicitacao' });
        } catch (err) {
            erro(err);
            botoes.forEach(b => b.disabled = false);
            return;
        }
        // A partir daqui o rascunho ja existe: falhas levam ao detalhe, onde o aluno conclui o que faltou.
        const pendencias = [];
        if (arquivo) {
            try {
                const corpo = new FormData();
                corpo.append('arquivo', arquivo);
                await api(`/${criada.id}/anexos`, { method: 'POST', body: corpo });
            } catch (err) {
                pendencias.push('o PDF não foi anexado (' + err.message + ')');
            }
        }
        if (enviar && pendencias.length === 0) {
            try {
                await api(`/${criada.id}/enviar`, { method: 'POST', json: {}, acao: true });
                return navegar('#/s/' + criada.id, 'Solicitação enviada para a secretaria.', true);
            } catch (err) {
                pendencias.push('o envio falhou (' + err.message + ')');
            }
        }
        if (pendencias.length)
            return navegar('#/s/' + criada.id, 'Rascunho salvo, mas ' + pendencias.join('; ') + '. Corrija aqui e tente de novo.');
        navegar('#/s/' + criada.id, arquivo
            ? 'Rascunho salvo com o PDF anexado. Clique em "Enviar solicitação" quando estiver pronto.'
            : 'Rascunho salvo. Anexe o PDF e clique em "Enviar solicitação".', true);
    };
}
