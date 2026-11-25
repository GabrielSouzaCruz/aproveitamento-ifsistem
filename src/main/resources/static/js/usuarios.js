'use strict';

/* Tela de usuarios da secretaria (SERVIDOR e ADMINISTRADOR), dentro do Aproveitamento.
 * Todas as chamadas vao direto a API /usuarios do ifsistem-auth (authUrl de GET /config, via js/sso.js),
 * com o token do proprio usuario (IfsistemSSO.fetch). Sem prompt/confirm/alert. */

const PERFIL_ROTULO = { ALUNO: 'Aluno', PROFESSOR: 'Professor', SERVIDOR: 'Servidor (secretaria)', ADMINISTRADOR: 'Administrador' };
const PERFIL_CURTO = { SERVIDOR: 'Servidor' };
const TAMANHO_PAGINA = 20;

/** Estado da lista; lembrado enquanto a pagina estiver aberta. */
const estadoUsuarios = { pagina: 0, perfil: '', busca: '', resposta: null };

const apenasDigitos = v => String(v ?? '').replace(/\D/g, '');

function mascararCpf(valor) {
    const d = apenasDigitos(valor).slice(0, 11);
    return d.replace(/^(\d{3})(\d)/, '$1.$2').replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3')
        .replace(/\.(\d{3})(\d)/, '.$1-$2');
}

/** Chamada a API de usuarios do auth. Erros viram ErroApi com a mensagem do auth quando houver. */
async function apiAuth(caminho, opcoes = {}) {
    const init = { method: opcoes.method || 'GET' };
    if (opcoes.json !== undefined) {
        init.headers = { 'Content-Type': 'application/json' };
        init.body = JSON.stringify(opcoes.json);
    }
    let r;
    try {
        r = await IfsistemSSO.fetch((window.IFSISTEM_AUTH_URL || '') + caminho, init);
    } catch (e) {
        if (e && e.message === 'Não autenticado.') { entrar(); throw new ErroApi(401, mensagemAmigavel(401)); }
        throw new ErroApi(0, 'Falha de comunicação com o servidor de login.');
    }
    if (r.status === 401) { entrar(); throw new ErroApi(401, mensagemAmigavel(401)); }
    if (r.status === 204) return null;
    let corpo = null;
    try { corpo = await r.json(); } catch (e) { /* sem corpo */ }
    if (!r.ok) {
        const msg = r.status === 403 ? 'Você não tem permissão para esta ação.'
            : (corpo && corpo.erro) || 'Não foi possível concluir a operação (erro ' + r.status + ').';
        throw new ErroApi(r.status, msg, corpo);
    }
    return corpo;
}

/* ---------- Modal com formulario ---------- */

/* `corpo` e HTML ja escapado. `aoConfirmar(form)` devolve Promise; se lancar Error, a mensagem aparece
 * no modal e ele continua aberto. Resolve true se concluiu, false se cancelou. */
function modalForm({ titulo, mensagem, corpo = '', textoOk = 'Confirmar', perigo = false, aoConfirmar = async () => {} }) {
    return new Promise(resolve => {
        const anterior = document.activeElement;
        const id = 'modal-' + Math.random().toString(36).slice(2);
        const fundo = document.createElement('div');
        fundo.className = 'modal-fundo';
        fundo.innerHTML = `
            <form class="modal" role="dialog" aria-modal="true" aria-labelledby="${id}" novalidate>
                <h2 id="${id}">${esc(titulo)}</h2>
                ${mensagem ? `<p class="modal-msg">${esc(mensagem)}</p>` : ''}
                <div class="modal-erro modal-erro-form" role="alert" hidden></div>
                ${corpo}
                <div class="modal-acoes">
                    <button type="button" class="btn btn-secundario" data-modal="cancelar">Cancelar</button>
                    <button type="submit" class="btn ${perigo ? 'btn-perigo-cheio' : 'btn-primario'}">${esc(textoOk)}</button>
                </div>
            </form>`;
        document.body.appendChild(fundo);
        const form = fundo.querySelector('form');
        const erroEl = fundo.querySelector('.modal-erro-form');
        const ok = form.querySelector('[type=submit]');

        function fechar(resultado) {
            document.removeEventListener('keydown', teclas, true);
            fundo.remove();
            if (anterior && anterior.focus) anterior.focus();
            resolve(resultado);
        }
        function teclas(e) {
            if (e.key === 'Escape') { e.preventDefault(); fechar(false); }
            else if (e.key === 'Tab') {
                const foco = [...form.querySelectorAll('input:not([type=hidden]):not(:disabled), select:not(:disabled), button:not(:disabled)')];
                if (!foco.length) return;
                const i = foco.indexOf(document.activeElement);
                if (e.shiftKey && i <= 0) { e.preventDefault(); foco[foco.length - 1].focus(); }
                else if (!e.shiftKey && i === foco.length - 1) { e.preventDefault(); foco[0].focus(); }
            }
        }
        document.addEventListener('keydown', teclas, true);
        fundo.addEventListener('mousedown', e => { if (e.target === fundo) fechar(false); });
        form.querySelector('[data-modal="cancelar"]').onclick = () => fechar(false);
        form.onsubmit = async e => {
            e.preventDefault();
            erroEl.hidden = true;
            ok.disabled = true;
            try {
                await aoConfirmar(form);
                fechar(true);
            } catch (ex) {
                erroEl.textContent = ex.message || 'Não foi possível concluir.';
                erroEl.hidden = false;
                ok.disabled = false;
                ok.focus();
            }
        };
        form.querySelectorAll('[data-mostrar]').forEach(c => {
            c.onchange = () => { form.querySelector('#' + c.dataset.mostrar).type = c.checked ? 'text' : 'password'; };
        });
        const cpf = form.querySelector('[data-cpf]');
        if (cpf) cpf.addEventListener('input', () => { cpf.value = mascararCpf(cpf.value); });
        (form.querySelector('input:not([type=hidden]):not(:disabled), select') || ok).focus();
    });
}

function campoSenha(id, rotulo) {
    return `
        <div class="campo">
            <label for="${id}">${esc(rotulo)}</label>
            <input id="${id}" name="senha" type="password" autocomplete="new-password" maxlength="72">
            <label class="mostrar-senha"><input type="checkbox" data-mostrar="${id}"> Mostrar senha</label>
            <p class="dica">Mínimo de 8 caracteres.</p>
        </div>`;
}

function validarSenha(s) {
    if (s.length < 8) throw new Error('A senha deve ter ao menos 8 caracteres.');
    if (new TextEncoder().encode(s).length > 72) throw new Error('A senha é longa demais (máximo de 72 caracteres).');
}

// ---------- Lista ----------

function linhaUsuario(u, eu) {
    return `
        <tr>
            <td class="celula-nome" data-rotulo="Nome"><strong>${esc(u.nome)}</strong><span class="sub">${esc(u.email)}</span></td>
            <td class="nowrap" data-rotulo="Perfil"><span class="valor">${esc(PERFIL_CURTO[u.perfil] || PERFIL_ROTULO[u.perfil] || u.perfil)}<span class="sub">CPF ${esc(mascararCpf(u.cpf))}</span></span></td>
            <td class="nowrap" data-rotulo="Situação">${seloAtivo(u.ativo)}</td>
            <td class="celula-acoes"><span class="acoes-linha">
                <button type="button" class="btn btn-sec btn-mini" data-acao="editar" data-id="${esc(u.id)}">Editar</button>
                <button type="button" class="btn btn-sec btn-mini" data-acao="senha" data-id="${esc(u.id)}">Redefinir senha</button>
                ${u.id === eu.id
                    ? '<span class="muted pequeno">Você</span>'
                    : `<button type="button" class="btn btn-sec btn-mini" data-acao="ativo" data-id="${esc(u.id)}">${u.ativo ? 'Inativar' : 'Ativar'}</button>`}
            </span></td>
        </tr>`;
}

async function buscarUsuarios() {
    const e = estadoUsuarios;
    const params = new URLSearchParams({ page: e.pagina, size: TAMANHO_PAGINA, sort: 'nome' });
    if (e.perfil) params.set('perfil', e.perfil);
    if (e.busca.trim()) params.set('q', e.busca.trim());
    return apiAuth('/usuarios?' + params);
}

async function telaUsuarios() {
    titulo('Usuários');
    const eu = usuarioAtual();
    app.innerHTML = '<p class="muted carregando">Carregando usuários</p>';
    const e = estadoUsuarios;

    app.innerHTML = `
        ${hero('Usuários', 'Cadastre professores e servidores e cuide dos acessos ao IFSISTEM.')}

        <section class="card">
            <div class="card-cab">
                <div>
                    <h2>Usuários <span class="contador" id="usr-contador">0</span></h2>
                    <p class="card-sub">Alunos se cadastram sozinhos na tela de login. Aqui a secretaria cria professores e servidores.</p>
                </div>
                <button type="button" class="btn" id="usr-novo">Novo usuário</button>
            </div>
            <div class="filtros">
                <label>Buscar por nome ou CPF
                    <input id="usr-busca" type="search" placeholder="Nome ou CPF" autocomplete="off" value="${esc(e.busca)}"></label>
                <label>Perfil
                    <select id="usr-perfil">
                        <option value="">Todos</option>
                        ${Object.keys(PERFIL_ROTULO).map(k => `<option value="${k}"${k === e.perfil ? ' selected' : ''}>${esc(PERFIL_ROTULO[k])}</option>`).join('')}
                    </select></label>
            </div>
            <div class="tabela-caixa"><div class="tabela-wrap">
                <table class="responsiva">
                    <thead><tr><th>Nome</th><th>Perfil</th><th>Situação</th><th><span hidden>Ações</span></th></tr></thead>
                    <tbody id="usr-linhas"></tbody>
                </table>
            </div></div>
            <nav class="paginacao" id="usr-paginacao" aria-label="Paginação" hidden>
                <button type="button" class="btn btn-sec btn-mini" id="usr-ant">← Anterior</button>
                <span id="usr-pagina" class="muted pequeno"></span>
                <button type="button" class="btn btn-sec btn-mini" id="usr-prox">Próxima →</button>
            </nav>
        </section>`;

    let ultimo = 0; // descarta respostas antigas quando o usuario digita rapido
    async function recarregar() {
        const meu = ++ultimo;
        let r;
        try {
            r = await buscarUsuarios();
        } catch (ex) {
            if (meu === ultimo) erro(ex);
            return;
        }
        if (meu !== ultimo) return;
        if (r.conteudo.length === 0 && r.totalPaginas > 0 && e.pagina > 0) { e.pagina = r.totalPaginas - 1; return recarregar(); }
        e.resposta = r;
        document.getElementById('usr-contador').textContent = r.totalElementos;
        document.getElementById('usr-linhas').innerHTML = r.conteudo.length === 0
            ? '<tr><td colspan="4" class="vazio">Nenhum usuário encontrado.</td></tr>'
            : r.conteudo.map(u => linhaUsuario(u, eu)).join('');
        const nav = document.getElementById('usr-paginacao');
        nav.hidden = r.totalPaginas <= 1;
        document.getElementById('usr-pagina').textContent = `Página ${r.pagina + 1} de ${r.totalPaginas}`;
        document.getElementById('usr-ant').disabled = r.pagina <= 0;
        document.getElementById('usr-prox').disabled = r.pagina >= r.totalPaginas - 1;
    }

    async function aplicar(escrita, sucesso) {
        await escrita();
        await recarregar();
        mostrarAviso(sucesso, true);
    }

    let atraso = null;
    document.getElementById('usr-busca').oninput = ev => {
        e.busca = ev.target.value;
        e.pagina = 0;
        clearTimeout(atraso);
        atraso = setTimeout(recarregar, 300);
    };
    document.getElementById('usr-perfil').onchange = ev => { e.perfil = ev.target.value; e.pagina = 0; recarregar(); };
    document.getElementById('usr-ant').onclick = () => { e.pagina = Math.max(0, e.pagina - 1); recarregar(); };
    document.getElementById('usr-prox').onclick = () => { e.pagina++; recarregar(); };
    document.getElementById('usr-novo').onclick = () => novoUsuario(aplicar);
    document.getElementById('usr-linhas').onclick = ev => {
        const b = ev.target.closest('button[data-acao]');
        if (!b || !e.resposta) return;
        const u = e.resposta.conteudo.find(x => x.id === b.dataset.id);
        if (!u) return;
        ({ editar: editarUsuario, senha: redefinirSenha, ativo: alternarAtivo })[b.dataset.acao](u, eu, aplicar);
    };

    await recarregar();
}

// ---------- Acoes ----------

function novoUsuario(aplicar) {
    return modalForm({
        titulo: 'Novo usuário',
        textoOk: 'Criar usuário',
        corpo: `
            <div class="campo"><label for="n-nome">Nome completo</label><input id="n-nome" name="nome" autocomplete="off"></div>
            <div class="grade-campos">
                <div class="campo"><label for="n-cpf">CPF</label><input id="n-cpf" name="cpf" data-cpf inputmode="numeric" placeholder="000.000.000-00" autocomplete="off"></div>
                <div class="campo"><label for="n-perfil">Perfil</label>
                    <select id="n-perfil" name="perfil">
                        <option value="PROFESSOR">Professor</option>
                        <option value="SERVIDOR">Servidor (secretaria)</option>
                    </select></div>
            </div>
            <div class="campo"><label for="n-email">E-mail institucional</label>
                <input id="n-email" name="email" type="email" placeholder="nome@ifsuldeminas.edu.br" autocomplete="off"></div>
            ${campoSenha('n-senha', 'Senha inicial')}`,
        aoConfirmar: async form => {
            const v = nome => form.elements[nome].value.trim();
            const senha = form.elements.senha.value;
            if (!v('nome') || !v('cpf') || !v('email') || !senha) throw new Error('Preencha todos os campos.');
            validarSenha(senha);
            await aplicar(() => apiAuth('/usuarios', { method: 'POST', json: {
                nome: v('nome'), cpf: v('cpf'), email: v('email'), senhaInicial: senha, perfil: v('perfil')
            } }), 'Usuário criado.');
        }
    });
}

function editarUsuario(u, eu, aplicar) {
    const proprio = u.id === eu.id;
    const fixo = proprio || u.perfil === 'ADMINISTRADOR';
    const perfis = fixo ? [u.perfil] : ['ALUNO', 'PROFESSOR', 'SERVIDOR'];
    return modalForm({
        titulo: 'Editar usuário',
        textoOk: 'Salvar',
        corpo: `
            <div class="campo"><label for="e-nome">Nome completo</label><input id="e-nome" name="nome" value="${esc(u.nome)}" autocomplete="off"></div>
            <div class="campo"><label for="e-cpf">CPF</label><input id="e-cpf" value="${esc(mascararCpf(u.cpf))}" disabled></div>
            <div class="campo"><label for="e-email">E-mail</label><input id="e-email" name="email" type="email" value="${esc(u.email)}" autocomplete="off"></div>
            <div class="campo"><label for="e-tel">Telefone (opcional)</label><input id="e-tel" name="telefone" value="${esc(u.telefone || '')}" autocomplete="off"></div>
            <div class="campo"><label for="e-perfil">Perfil</label>
                <select id="e-perfil" name="perfil" ${fixo ? 'disabled' : ''}>
                    ${perfis.map(p => `<option value="${p}" ${p === u.perfil ? 'selected' : ''}>${esc(PERFIL_ROTULO[p] || p)}</option>`).join('')}
                </select>
                ${fixo ? `<p class="dica">${proprio ? 'Você não pode trocar o perfil da própria conta.' : 'O perfil Administrador não pode ser alterado aqui.'}</p>` : ''}
            </div>`,
        aoConfirmar: async form => {
            const nome = form.elements.nome.value.trim();
            const email = form.elements.email.value.trim();
            if (!nome || !email) throw new Error('Nome e e-mail são obrigatórios.');
            // O PUT substitui telefone, curso e periodo: reenvia o que ja existe para nao apagar.
            await aplicar(() => apiAuth('/usuarios/' + u.id, { method: 'PUT', json: {
                nome, email, telefone: form.elements.telefone.value.trim() || null,
                curso: u.curso, periodo: u.periodo, perfil: fixo ? null : form.elements.perfil.value
            } }), 'Usuário atualizado.');
        }
    });
}

function alternarAtivo(u, eu, aplicar) {
    const ativar = !u.ativo;
    return modalForm({
        titulo: ativar ? 'Ativar usuário' : 'Inativar usuário',
        mensagem: ativar
            ? `Ativar ${u.nome}? A pessoa voltará a conseguir entrar nos sistemas.`
            : `Inativar ${u.nome}? A pessoa não conseguirá mais entrar nos sistemas.`,
        textoOk: ativar ? 'Ativar' : 'Inativar',
        perigo: !ativar,
        aoConfirmar: () => aplicar(() => apiAuth('/usuarios/' + u.id + '/ativo', { method: 'PATCH', json: { ativo: ativar } }),
            ativar ? 'Usuário ativado.' : 'Usuário inativado.')
    });
}

function redefinirSenha(u, eu, aplicar) {
    return modalForm({
        titulo: 'Redefinir senha',
        mensagem: `Digite a nova senha de ${u.nome}. Informe-a à pessoa por um canal seguro.`,
        textoOk: 'Redefinir senha',
        corpo: campoSenha('s-senha', 'Nova senha'),
        aoConfirmar: async form => {
            const senha = form.elements.senha.value;
            validarSenha(senha);
            await aplicar(() => apiAuth('/usuarios/' + u.id + '/senha', { method: 'PUT', json: { senha } }),
                'Senha redefinida.');
        }
    });
}
