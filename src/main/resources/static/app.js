'use strict';

/* Tela de demonstracao do fluxo de aproveitamento (padrao visual IF Conect). JS puro, sem build.
 * A identidade e provisoria: o usuario escolhido vai nos headers X-Usuario-Id e X-Perfil. */

const API = '/aproveitamentos';

/** Usuarios fixos do seed do profile demo (DemoSeed). */
const USUARIOS = [
    { id: '11111111-1111-1111-1111-111111111111', perfil: 'ALUNO', papel: 'Aluno', area: 'Área do estudante',
      nome: 'Ana Aluna Demo', primeiroNome: 'Ana', saudacao: 'Bem-vinda de volta, Ana!',
      descricao: 'Cria, anexa documentos e envia solicitações.' },
    { id: '22222222-2222-2222-2222-222222222222', perfil: 'SERVIDOR', papel: 'Secretaria', area: 'Área da secretaria',
      nome: 'Sergio Servidor Demo', primeiroNome: 'Sergio', saudacao: 'Bem-vindo de volta, Sergio!',
      descricao: 'Confere documentação, devolve pendências e registra.' },
    { id: '33333333-3333-3333-3333-333333333333', perfil: 'PROFESSOR', papel: 'Coordenador', area: 'Área da coordenação',
      nome: 'Paula Professora Demo', primeiroNome: 'Paula', saudacao: 'Bem-vinda de volta, professora Paula!',
      descricao: 'Coordena ADS: analisa, vê o parecer da IA e decide.' }
];

/** Acoes por perfil e status de origem (espelha as classes de transicao do backend). */
const ACOES = [
    { acao: 'enviar', rotulo: 'Enviar solicitação', perfil: 'ALUNO', origens: ['RASCUNHO'] },
    { acao: 'reenviar', rotulo: 'Reenviar', perfil: 'ALUNO', origens: ['PENDENTE'] },
    { acao: 'conferir', rotulo: 'Conferir documentação', perfil: 'SERVIDOR', origens: ['ENVIADA'] },
    { acao: 'concluir-pre-diagnostico', rotulo: 'Concluir pré-diagnóstico', perfil: 'SERVIDOR', origens: ['CONFERENCIA'] },
    { acao: 'devolver-pendencia', rotulo: 'Devolver com pendência', perfil: 'SERVIDOR', origens: ['CONFERENCIA', 'ANALISE'], perigo: true },
    { acao: 'registrar', rotulo: 'Registrar no histórico', perfil: 'SERVIDOR', origens: ['DEFERIDA', 'INDEFERIDA'] },
    { acao: 'analisar', rotulo: 'Concluir análise', perfil: 'PROFESSOR', origens: ['ANALISE'] },
    { acao: 'deferir', rotulo: 'Deferir', perfil: 'PROFESSOR', origens: ['DECISAO'] },
    { acao: 'indeferir', rotulo: 'Indeferir', perfil: 'PROFESSOR', origens: ['DECISAO'], perigo: true }
];

const STATUS_ROTULO = {
    RASCUNHO: 'Rascunho', ENVIADA: 'Enviada', CONFERENCIA: 'Em conferência', PENDENTE: 'Pendente',
    PRE_DIAGNOSTICO: 'Pré-diagnóstico', ANALISE: 'Em análise', DECISAO: 'Aguardando decisão',
    DEFERIDA: 'Deferida', INDEFERIDA: 'Indeferida', REGISTRADA: 'Registrada'
};

const STATUS_FINAIS = ['DEFERIDA', 'INDEFERIDA', 'REGISTRADA'];

const RECOMENDACAO_ROTULO = {
    SUGERE_DEFERIR: 'Sugere deferir', SUGERE_INDEFERIR: 'Sugere indeferir', INCONCLUSIVO: 'Inconclusivo'
};

const STATUS_ANEXO_EDITAVEL = ['RASCUNHO', 'PENDENTE'];

const ICONES = {
    lista: '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></svg>',
    mais: '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="9"/><path d="M12 8v8M8 12h8"/></svg>',
    seta: '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 6l6 6-6 6"/></svg>',
    voltar: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>',
    pdf: '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/></svg>',
    ia: '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z"/><path d="M19 17l.8 2.2L22 20l-2.2.8L19 23l-.8-2.2L16 20l2.2-.8z"/></svg>',
    regerar: '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12a9 9 0 1 1-3-6.7L21 8"/><path d="M21 3v5h-5"/></svg>'
};

const app = document.getElementById('app');
let pollingParecer = null;

// ---------- Sessao ----------

function usuarioAtual() {
    const id = sessionStorage.getItem('usuarioId');
    return USUARIOS.find(u => u.id === id) || null;
}

function entrar(usuario) {
    sessionStorage.setItem('usuarioId', usuario.id);
    sessionStorage.setItem('perfil', usuario.perfil);
    irPara('#/');
}

function sair() {
    sessionStorage.removeItem('usuarioId');
    sessionStorage.removeItem('perfil');
    irPara('#/');
}

/** Troca de hash dispara o roteamento; se o hash ja for o mesmo, roteia direto. */
function irPara(hash) {
    if (location.hash === hash) rotear();
    else location.hash = hash;
}

// ---------- HTTP ----------

class ErroApi extends Error {
    constructor(status, mensagem) {
        super(mensagem);
        this.status = status;
    }
}

function mensagemAmigavel(status, acao) {
    switch (status) {
        case 400: return 'Dados inválidos. Confira os campos e tente novamente.';
        case 401: return 'Sessão inválida. Escolha o usuário novamente.';
        case 403: return acao
            ? 'Você não tem permissão para esta ação com o seu perfil.'
            : 'Acesso negado para o seu perfil.';
        case 404: return 'Registro não encontrado.';
        case 409: return acao
            ? 'Esta ação não é permitida no status atual da solicitação. A tela foi atualizada.'
            : 'Operação não permitida no status atual da solicitação.';
        case 413: return 'Arquivo maior que o limite de 5 MB.';
        case 415: return 'Apenas arquivos PDF são aceitos.';
        default: return 'Não foi possível concluir a operação (erro ' + status + ').';
    }
}

async function api(caminho, opcoes = {}) {
    const u = usuarioAtual();
    const headers = Object.assign({ 'X-Usuario-Id': u.id, 'X-Perfil': u.perfil }, opcoes.headers || {});
    if (opcoes.json !== undefined) {
        headers['Content-Type'] = 'application/json';
        opcoes.body = JSON.stringify(opcoes.json);
    }
    const resp = await fetch(API + caminho, { method: opcoes.method || 'GET', headers, body: opcoes.body });
    if (!resp.ok) {
        if (resp.status === 401) sair();
        throw new ErroApi(resp.status, mensagemAmigavel(resp.status, opcoes.acao));
    }
    if (opcoes.blob) return resp.blob();
    const texto = await resp.text();
    return texto ? JSON.parse(texto) : null;
}

// ---------- Utilitarios de UI ----------

function esc(valor) {
    return String(valor ?? '').replace(/[&<>"']/g, c =>
        ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function data(iso) {
    if (!iso) return '—';
    return new Date(iso).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
}

function statusBadge(status) {
    return `<span class="status st-${esc(status)}">${esc(STATUS_ROTULO[status] || status)}</span>`;
}

function nomeUsuario(id) {
    const u = USUARIOS.find(x => x.id === id);
    return u ? `${u.nome} (${u.papel})` : 'Sistema';
}

function iniciais(nome) {
    const partes = nome.split(' ');
    return (partes[0][0] + (partes[1] ? partes[1][0] : '')).toUpperCase();
}

function tamanho(bytes) {
    if (bytes == null) return '';
    if (bytes < 1024) return bytes + ' B';
    return bytes < 1024 * 1024 ? (bytes / 1024).toFixed(0) + ' KB' : (bytes / 1024 / 1024).toFixed(1) + ' MB';
}

function acoesDisponiveis(perfil, status) {
    return ACOES.filter(a => a.perfil === perfil && a.origens.includes(status));
}

const aviso = document.getElementById('aviso');
let avisoTimer = null;

function mostrarAviso(mensagem, ok = false) {
    clearTimeout(avisoTimer);
    aviso.className = 'aviso' + (ok ? ' ok' : '');
    aviso.innerHTML = `<span>${esc(mensagem)}</span><button type="button" aria-label="Fechar">×</button>`;
    aviso.hidden = false;
    aviso.querySelector('button').onclick = esconderAviso;
    avisoTimer = setTimeout(esconderAviso, ok ? 4000 : 8000);
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

function esconderAviso() {
    aviso.hidden = true;
}

function erro(e) {
    mostrarAviso(e instanceof ErroApi ? e.message : 'Falha de comunicação com o servidor.');
}

// ---------- Moldura (menu lateral e barra do topo) ----------

function montarMoldura(u, rota) {
    document.body.classList.toggle('sem-sessao', !u);
    if (!u) return;
    document.getElementById('area-usuario').textContent = u.area;
    document.getElementById('usuario-nome').textContent = u.nome;
    document.getElementById('usuario-papel').textContent = u.papel;
    document.getElementById('usuario-iniciais').textContent = iniciais(u.nome);
    document.getElementById('hoje').textContent = new Date().toLocaleDateString('pt-BR',
        { day: 'numeric', month: 'long', year: 'numeric' }) + ' • ' +
        new Date().toLocaleDateString('pt-BR', { weekday: 'long' });

    const itens = [{ href: '#/', rotulo: 'Aproveitamentos', icone: ICONES.lista, ativo: rota !== 'nova' }];
    if (u.perfil === 'ALUNO')
        itens.push({ href: '#/nova', rotulo: 'Nova solicitação', icone: ICONES.mais, ativo: rota === 'nova' });
    document.getElementById('menu').innerHTML = itens.map(i =>
        `<a href="${i.href}" class="${i.ativo ? 'ativo' : ''}"${i.ativo ? ' aria-current="page"' : ''}>${i.icone}<span>${esc(i.rotulo)}</span></a>`).join('');
}

function titulo(texto) {
    document.getElementById('titulo-pagina').textContent = texto;
    document.title = texto + ' · IF Conect';
}

// ---------- Roteamento ----------

async function rotear() {
    clearTimeout(pollingParecer);
    const u = usuarioAtual();
    const hash = location.hash.replace(/^#/, '');
    const detalhe = hash.match(/^\/s\/([0-9a-f-]{36})$/i);
    const nova = hash === '/nova' && u && u.perfil === 'ALUNO';
    montarMoldura(u, nova ? 'nova' : 'lista');
    if (!u) return telaLogin();
    try {
        if (detalhe) await telaDetalhe(detalhe[1]);
        else if (nova) telaNova();
        else await telaLista();
    } catch (e) {
        if (detalhe) navegar('#/', e instanceof ErroApi ? e.message : 'Falha de comunicação com o servidor.');
        else erro(e);
    }
}

/** Aviso a exibir depois da proxima troca de tela (a troca limpa avisos antigos). */
let avisoPendente = null;

function navegar(hash, mensagem, ok = false) {
    avisoPendente = mensagem ? [mensagem, ok] : null;
    location.hash = hash;
}

window.addEventListener('hashchange', async () => {
    esconderAviso();
    const pendente = avisoPendente;
    avisoPendente = null;
    await rotear();
    if (pendente) mostrarAviso(...pendente);
});
document.getElementById('btn-sair').onclick = sair;

// ---------- Escolha de usuario ----------

function telaLogin() {
    document.title = 'Aproveitamento de Disciplinas · IF Conect';
    app.innerHTML = `
        <section class="login">
            <div class="marca">IF <span>Conect</span></div>
            <p class="sobretitulo">Aproveitamento de disciplinas</p>
            <h1>Quem está acessando?</h1>
            <p class="muted">Ambiente de demonstração: escolha um dos usuários de exemplo para entrar.</p>
            <div class="perfis">
                ${USUARIOS.map((u, i) => `
                    <button type="button" class="perfil" data-i="${i}">
                        <span class="avatar">${esc(iniciais(u.nome))}</span>
                        <span class="contador">${esc(u.papel)}</span>
                        <strong>${esc(u.nome)}</strong>
                        <span class="muted">${esc(u.descricao)}</span>
                    </button>`).join('')}
            </div>
        </section>`;
    app.querySelectorAll('.perfil').forEach(b => b.onclick = () => entrar(USUARIOS[b.dataset.i]));
}

// ---------- Lista ----------

async function telaLista() {
    const u = usuarioAtual();
    titulo(u.perfil === 'ALUNO' ? 'Minhas solicitações' : 'Aproveitamentos');
    app.innerHTML = '<p class="muted carregando">Carregando solicitações</p>';
    const lista = await api('');
    lista.sort((a, b) => (b.atualizadoEm || '').localeCompare(a.atualizadoEm || ''));

    const aguardando = lista.filter(s => acoesDisponiveis(u.perfil, s.status).length > 0).length;
    const concluidas = lista.filter(s => STATUS_FINAIS.includes(s.status)).length;
    const agora = new Date();
    const mes = agora.toLocaleDateString('pt-BR', { month: 'long' });

    app.innerHTML = `
        <section class="boas-vindas">
            <p class="sobretitulo">${agora.getFullYear()} | ${esc(mes)}</p>
            <h2>${esc(u.saudacao)}</h2>
            <p>${aguardando === 0
                ? 'Nenhuma solicitação aguarda ação sua agora.'
                : `Você tem ${aguardando} ${aguardando === 1 ? 'solicitação que aguarda' : 'solicitações que aguardam'} ação sua.`}</p>
            ${u.perfil === 'ALUNO' ? `<div class="acoes"><a class="btn" href="#/nova">${ICONES.mais} Nova solicitação</a></div>` : ''}
        </section>

        <section class="numeros">
            <div class="numero"><span>${u.perfil === 'ALUNO' ? 'Minhas solicitações' : 'Solicitações no sistema'}</span><strong>${lista.length}</strong></div>
            <div class="numero ${aguardando ? 'alerta' : ''}"><span>Aguardando sua ação</span><strong>${aguardando}</strong></div>
            <div class="numero realce"><span>Concluídas</span><strong>${concluidas}</strong></div>
        </section>

        <section class="card card-tabela">
            <div class="card-cab">
                <h3>Solicitações <span class="contador">${lista.length}</span></h3>
            </div>
            <div class="tabela-wrap">
                <table class="responsiva">
                    <thead><tr><th>Disciplina destino</th><th>Curso</th><th>Status</th><th>Atualizada em</th><th><span hidden>Abrir</span></th></tr></thead>
                    <tbody>
                        ${lista.length === 0 ? '<tr><td colspan="5" class="vazio">Nenhuma solicitação encontrada.</td></tr>' : ''}
                        ${lista.map(s => `
                            <tr class="clicavel" data-id="${esc(s.id)}" tabindex="0">
                                <td data-rotulo="Disciplina"><strong>${esc(s.disciplinaDestino)}</strong>
                                    ${acoesDisponiveis(u.perfil, s.status).length ? '<span class="sub">Aguarda sua ação</span>' : ''}</td>
                                <td data-rotulo="Curso">${esc(s.curso)}</td>
                                <td data-rotulo="Status">${statusBadge(s.status)}</td>
                                <td data-rotulo="Atualizada">${data(s.atualizadoEm)}</td>
                                <td class="seta">${ICONES.seta}</td>
                            </tr>`).join('')}
                    </tbody>
                </table>
            </div>
        </section>`;
    app.querySelectorAll('tr.clicavel').forEach(tr => {
        const abrir = () => { location.hash = '#/s/' + tr.dataset.id; };
        tr.onclick = abrir;
        tr.onkeydown = e => { if (e.key === 'Enter') abrir(); };
    });
}

// ---------- Detalhe ----------

async function telaDetalhe(id) {
    const u = usuarioAtual();
    titulo('Solicitação de aproveitamento');
    app.innerHTML = '<p class="muted carregando">Carregando solicitação</p>';
    const [s, historico] = await Promise.all([api('/' + id), api('/' + id + '/historico')]);
    const acoes = acoesDisponiveis(u.perfil, s.status);
    const podeAnexar = u.perfil === 'ALUNO' && STATUS_ANEXO_EDITAVEL.includes(s.status);
    const veParecer = u.perfil !== 'ALUNO';

    app.innerHTML = `
        <a class="voltar" href="#/">${ICONES.voltar} Voltar para a lista</a>

        <section class="card">
            <div class="cabecalho-detalhe">
                <div>
                    <p class="sobretitulo">${esc(s.curso)} · ${esc(s.cargaHorariaDestino)} h</p>
                    <h2>${esc(s.disciplinaDestino)}</h2>
                    <span class="muted pequeno">Atualizada em ${data(s.atualizadoEm)}</span>
                </div>
                ${statusBadge(s.status)}
            </div>
            ${acoes.length ? `
                <div class="campo" style="margin-top:1.25rem">
                    <label for="obs">Observação (opcional)</label>
                    <textarea id="obs" rows="2" maxlength="2000" placeholder="Registrada no histórico junto com a ação"></textarea>
                </div>
                <div class="acoes">
                    ${acoes.map(a => `<button type="button" class="btn ${a.perigo ? 'btn-perigo' : ''}" data-acao="${a.acao}">${esc(a.rotulo)}</button>`).join('')}
                </div>`
            : '<p class="muted" style="margin:1rem 0 0">Nenhuma ação disponível para o seu perfil neste status.</p>'}
        </section>

        ${veParecer ? '<section class="card destaque parecer" id="parecer"></section>' : ''}

        <div class="grade">
            <section class="card">
                <h3>Dados da solicitação</h3>
                <dl class="dados">
                    <dt>Aluno</dt><dd>${esc(nomeUsuario(s.alunoId))}</dd>
                    <dt>Curso</dt><dd>${esc(s.curso)}</dd>
                    <dt>Disciplina</dt><dd>${esc(s.disciplinaDestino)}</dd>
                    <dt>Carga horária</dt><dd>${esc(s.cargaHorariaDestino)} h</dd>
                    <dt>Criada em</dt><dd>${data(s.criadoEm)}</dd>
                </dl>
                <h3 style="margin-top:1.5rem">Disciplinas cursadas <span class="contador">${s.disciplinas.length}</span></h3>
                <div class="tabela-wrap">
                    <table class="responsiva">
                        <thead><tr><th>Disciplina</th><th>Instituição</th><th>Carga</th></tr></thead>
                        <tbody>
                            ${s.disciplinas.map(d => `
                                <tr>
                                    <td data-rotulo="Disciplina">${esc(d.nome)}</td>
                                    <td data-rotulo="Instituição">${esc(d.instituicao)}</td>
                                    <td data-rotulo="Carga">${esc(d.cargaHoraria)} h</td>
                                </tr>`).join('')}
                        </tbody>
                    </table>
                </div>
                ${s.preDiagnostico ? `
                    <h3 style="margin-top:1.5rem">Pré-diagnóstico</h3>
                    <p class="pequeno">Carga: <strong>${esc(s.preDiagnostico.percentualCarga ?? '—')}%</strong>
                    · Conteúdo: <strong>${esc(s.preDiagnostico.percentualConteudo ?? '—')}%</strong></p>
                    ${s.preDiagnostico.justificativa ? `<p class="pequeno muted">${esc(s.preDiagnostico.justificativa)}</p>` : ''}` : ''}
            </section>

            <section class="card">
                <h3>Histórico de status</h3>
                ${historico.length ? `
                    <ol class="linha-tempo">
                        ${historico.map(h => `
                            <li>
                                ${h.statusAnterior ? statusBadge(h.statusAnterior) + ' → ' : ''}${statusBadge(h.statusNovo)}
                                <div class="pequeno muted">${data(h.criadoEm)} · ${esc(nomeUsuario(h.responsavelId))}</div>
                                ${h.observacao ? `<div class="obs">“${esc(h.observacao)}”</div>` : ''}
                            </li>`).join('')}
                    </ol>` : '<p class="muted">Sem movimentações ainda.</p>'}
            </section>
        </div>

        <section class="card" style="margin-top:1.25rem">
            <h3>Anexos <span class="contador">${s.anexos.length}</span></h3>
            ${s.anexos.length ? `
                <ul class="anexos">
                    ${s.anexos.map(a => `
                        <li>
                            <span class="anexo-nome">${ICONES.pdf} <span>${esc(a.nomeArquivo)}
                                <span class="muted pequeno">· ${tamanho(a.tamanho)} · ${data(a.criadoEm)}</span></span></span>
                            <span class="acoes-anexo">
                                <button type="button" class="btn btn-sec btn-mini" data-baixar="${esc(a.id)}" data-nome="${esc(a.nomeArquivo)}">Baixar</button>
                                ${podeAnexar ? `<button type="button" class="btn btn-perigo btn-mini" data-remover="${esc(a.id)}">Remover</button>` : ''}
                            </span>
                        </li>`).join('')}
                </ul>` : '<p class="muted">Nenhum anexo.</p>'}
            ${podeAnexar ? `
                <form id="form-anexo" class="acoes">
                    <input type="file" id="arquivo" accept="application/pdf,.pdf" required aria-label="Arquivo PDF" style="flex:1;min-width:200px">
                    <button type="submit" class="btn">Enviar PDF</button>
                </form>
                <p class="muted pequeno">Somente PDF, até 5 MB.</p>` : ''}
        </section>`;

    app.querySelectorAll('[data-acao]').forEach(b => b.onclick = () => executarAcao(id, b.dataset.acao, b));
    app.querySelectorAll('[data-baixar]').forEach(b => b.onclick = () => baixarAnexo(id, b.dataset.baixar, b.dataset.nome));
    app.querySelectorAll('[data-remover]').forEach(b => b.onclick = () => removerAnexo(id, b.dataset.remover));
    const formAnexo = document.getElementById('form-anexo');
    if (formAnexo) formAnexo.onsubmit = e => { e.preventDefault(); enviarAnexo(id, formAnexo); };
    if (veParecer) carregarParecer(id);
}

async function executarAcao(id, acao, botao) {
    const obs = document.getElementById('obs');
    const observacao = obs && obs.value.trim() ? obs.value.trim() : null;
    app.querySelectorAll('[data-acao]').forEach(b => b.disabled = true);
    try {
        const r = await api(`/${id}/${acao}`, { method: 'POST', json: observacao ? { observacao } : {}, acao: true });
        await telaDetalhe(id);
        mostrarAviso(`Ação "${botao.textContent}" concluída. Novo status: ${STATUS_ROTULO[r.status] || r.status}.`, true);
    } catch (e) {
        erro(e);
        if (e.status === 409) await telaDetalhe(id).catch(() => {});
        else app.querySelectorAll('[data-acao]').forEach(b => b.disabled = false);
    }
}

// ---------- Anexos ----------

async function enviarAnexo(id, form) {
    const arquivo = form.querySelector('#arquivo').files[0];
    if (!arquivo) return;
    if (arquivo.size > 5 * 1024 * 1024) return mostrarAviso('Arquivo maior que o limite de 5 MB.');
    const corpo = new FormData();
    corpo.append('arquivo', arquivo);
    form.querySelector('button').disabled = true;
    try {
        await api(`/${id}/anexos`, { method: 'POST', body: corpo });
        await telaDetalhe(id);
        mostrarAviso('Anexo enviado.', true);
    } catch (e) {
        erro(e);
        form.querySelector('button').disabled = false;
    }
}

async function baixarAnexo(id, anexoId, nome) {
    try {
        const blob = await api(`/${id}/anexos/${anexoId}`, { blob: true });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = nome || 'anexo.pdf';
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (e) {
        erro(e);
    }
}

async function removerAnexo(id, anexoId) {
    if (!confirm('Remover este anexo?')) return;
    try {
        await api(`/${id}/anexos/${anexoId}`, { method: 'DELETE' });
        await telaDetalhe(id);
        mostrarAviso('Anexo removido.', true);
    } catch (e) {
        erro(e);
    }
}

// ---------- Parecer da IA ----------

async function carregarParecer(id, tentativas = 0) {
    const card = document.getElementById('parecer');
    if (!card) return;
    let p = null;
    try {
        p = await api(`/${id}/parecer-ia`);
    } catch (e) {
        if (e.status !== 404) { card.innerHTML = cabecalhoParecer() + `<p class="muted">${esc(e.message)}</p>`; return; }
    }
    if (!document.getElementById('parecer')) return; // usuario saiu da tela

    let corpo;
    if (!p) {
        corpo = '<p class="muted">Ainda não há parecer. Ele é gerado automaticamente quando o aluno envia a solicitação.</p>';
    } else if (p.status === 'PROCESSANDO') {
        corpo = '<p class="muted carregando">A IA está analisando a solicitação</p>';
    } else if (p.status === 'FALHOU') {
        corpo = `<p class="rec rec-SUGERE_INDEFERIR">Falha ao gerar o parecer</p>
                 ${p.erro ? `<p class="muted pequeno">${esc(p.erro)}</p>` : ''}`;
    } else {
        const sim = Math.max(0, Math.min(100, p.similaridade ?? 0));
        const faixa = sim < 50 ? 'baixa' : sim < 75 ? 'media' : '';
        corpo = `
            <span class="rec rec-${esc(p.recomendacao)}">${esc(RECOMENDACAO_ROTULO[p.recomendacao] || p.recomendacao || '—')}</span>
            <div class="pequeno">Similaridade de conteúdo: <strong>${sim}%</strong></div>
            <div class="barra ${faixa}" role="progressbar" aria-valuenow="${sim}" aria-valuemin="0" aria-valuemax="100" aria-label="Similaridade"><span style="width:${sim}%"></span></div>
            <p>${esc(p.justificativa || '')}</p>
            <p class="muted pequeno" style="margin-bottom:0">Gerado em ${data(p.geradoEm)}. Sugestão de apoio: a decisão é do coordenador.</p>`;
    }
    card.innerHTML = cabecalhoParecer() + corpo;
    const btn = card.querySelector('#btn-regerar');
    if (btn) {
        btn.disabled = p && p.status === 'PROCESSANDO';
        btn.onclick = () => regerarParecer(id, btn);
    }

    // O parecer e gerado de forma assincrona: enquanto estiver processando (ou logo apos regerar,
    // antes de existir), consulta de novo a cada 2 s, no maximo 15 vezes.
    const aguardando = (p && p.status === 'PROCESSANDO') || (!p && tentativas > 0);
    if (aguardando && tentativas < 15)
        pollingParecer = setTimeout(() => carregarParecer(id, tentativas + 1), 2000);
}

function cabecalhoParecer() {
    return `<div class="card-cab">
                <h3>${ICONES.ia} Pré-parecer da IA</h3>
                <button type="button" class="btn btn-sec btn-mini" id="btn-regerar">${ICONES.regerar} Regerar</button>
            </div>`;
}

async function regerarParecer(id, botao) {
    botao.disabled = true;
    try {
        await api(`/${id}/parecer-ia/regerar`, { method: 'POST', acao: true });
        mostrarAviso('Novo parecer solicitado. Ele aparece aqui em alguns segundos.', true);
        clearTimeout(pollingParecer);
        carregarParecer(id, 1);
    } catch (e) {
        erro(e);
        botao.disabled = false;
    }
}

// ---------- Nova solicitacao ----------

function linhaDisciplina() {
    return `
        <div class="campos linha-disc">
            <div><label>Disciplina cursada<input name="nome" required placeholder="Ex.: Banco de Dados I"></label></div>
            <div><label>Instituição<input name="instituicao" required placeholder="Ex.: UNIFAL"></label></div>
            <div><label>Carga (h)<input name="cargaHoraria" type="number" min="1" required></label></div>
            <div><button type="button" class="btn btn-perigo btn-mini remover-disc" title="Remover disciplina" aria-label="Remover disciplina">×</button></div>
        </div>`;
}

function telaNova() {
    titulo('Nova solicitação');
    app.innerHTML = `
        <a class="voltar" href="#/">${ICONES.voltar} Voltar para a lista</a>
        <form id="form-nova" class="card">
            <h3>Disciplina que deseja aproveitar</h3>
            <div class="campos-2">
                <div class="campo"><label for="curso">Curso</label><input id="curso" required value="ADS"></div>
                <div class="campo"><label for="disciplinaDestino">Disciplina destino</label><input id="disciplinaDestino" required placeholder="Ex.: Banco de Dados"></div>
                <div class="campo"><label for="cargaHorariaDestino">Carga (h)</label><input id="cargaHorariaDestino" type="number" min="1" required></div>
            </div>
            <h3 style="margin-top:.75rem">Disciplinas já cursadas</h3>
            <div id="disciplinas">${linhaDisciplina()}</div>
            <button type="button" class="btn btn-sec btn-mini" id="add-disc">+ Adicionar disciplina</button>
            <div class="acoes" style="margin-top:1.5rem">
                <button type="submit" class="btn">Criar rascunho</button>
                <a class="btn btn-sec" href="#/">Cancelar</a>
            </div>
            <p class="muted pequeno">A solicitação é criada como rascunho. Depois você pode anexar PDFs e enviar.</p>
        </form>`;

    const lista = document.getElementById('disciplinas');
    const ligarRemover = () => lista.querySelectorAll('.remover-disc').forEach(b => b.onclick = () => {
        if (lista.children.length > 1) b.closest('.linha-disc').remove();
    });
    ligarRemover();
    document.getElementById('add-disc').onclick = () => {
        lista.insertAdjacentHTML('beforeend', linhaDisciplina());
        ligarRemover();
    };

    const form = document.getElementById('form-nova');
    form.onsubmit = async e => {
        e.preventDefault();
        const valor = sel => form.querySelector(sel).value.trim();
        const dados = {
            alunoId: usuarioAtual().id,
            curso: valor('#curso'),
            disciplinaDestino: valor('#disciplinaDestino'),
            cargaHorariaDestino: Number(valor('#cargaHorariaDestino')),
            disciplinas: [...lista.querySelectorAll('.linha-disc')].map(l => ({
                nome: l.querySelector('[name=nome]').value.trim(),
                instituicao: l.querySelector('[name=instituicao]').value.trim(),
                cargaHoraria: Number(l.querySelector('[name=cargaHoraria]').value)
            }))
        };
        const botao = form.querySelector('[type=submit]');
        botao.disabled = true;
        try {
            const criada = await api('', { method: 'POST', json: dados });
            navegar('#/s/' + criada.id, 'Rascunho criado. Anexe os documentos e clique em "Enviar solicitação".', true);
        } catch (err) {
            erro(err);
            botao.disabled = false;
        }
    };
}

rotear();
