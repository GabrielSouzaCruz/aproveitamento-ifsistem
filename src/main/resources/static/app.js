'use strict';

/* Tela de demonstracao do fluxo de aproveitamento (padrao visual IF Conect). JS puro, sem build.
 * Autenticacao: login.html obtem o JWT (POST /auth/login), guardado em sessionStorage e enviado
 * como Authorization: Bearer. Um 401 devolve para o login. */

const API = '/aproveitamentos';

/** Rotulos por perfil (o usuario em si vem do login). */
const PERFIS = {
    ALUNO: { papel: 'Aluno', area: 'Área do estudante' },
    SERVIDOR: { papel: 'Secretaria', area: 'Área da secretaria' },
    PROFESSOR: { papel: 'Coordenador', area: 'Área da coordenação' }
};

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

ICONES.livro = '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20V3H6.5A2.5 2.5 0 0 0 4 5.5z"/><path d="M4 19.5A2.5 2.5 0 0 0 6.5 22H20v-5"/></svg>';
ICONES.pessoas = '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="9" cy="8" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0"/><path d="M16 4.5a3.5 3.5 0 0 1 0 7M18.5 14.5A6.5 6.5 0 0 1 21.5 20"/></svg>';
ICONES.chave = '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="8" cy="15" r="4"/><path d="M10.8 12.2L20 3M16 7l3 3"/></svg>';

/** Menu por perfil: `chave` casa com a chave da rota ativa (ver ROTAS). */
const MENUS = {
    ALUNO: [
        { chave: 'inicio', href: '#/', rotulo: 'Minhas solicitações', icone: ICONES.lista },
        { chave: 'nova', href: '#/nova', rotulo: 'Nova solicitação', icone: ICONES.mais }
    ],
    SERVIDOR: [
        { chave: 'inicio', href: '#/', rotulo: 'Fila de solicitações', icone: ICONES.lista },
        { chave: 'cursos', href: '#/cursos', rotulo: 'Cursos e disciplinas', icone: ICONES.livro },
        { chave: 'coordenadores', href: '#/coordenadores', rotulo: 'Coordenadores', icone: ICONES.chave },
        { chave: 'usuarios', href: '#/usuarios', rotulo: 'Usuários', icone: ICONES.pessoas }
    ],
    PROFESSOR: [
        { chave: 'inicio', href: '#/', rotulo: 'Para analisar', icone: ICONES.lista }
    ]
};

const app = document.getElementById('app');
let pollingParecer = null;
const RECOMENDACAO_TOM = { SUGERE_DEFERIR: 'deferir', SUGERE_INDEFERIR: 'indeferir', INCONCLUSIVO: 'inconclusivo' };
let pollingAnexos = null;

// ---------- Sessao ----------

function usuarioAtual() {
    const token = sessionStorage.getItem('token');
    let u = null;
    try { u = JSON.parse(sessionStorage.getItem('usuario')); } catch (e) { /* sessao corrompida */ }
    if (!token || !u) return null;
    const exp = Date.parse(sessionStorage.getItem('expiraEm') || '');
    if (exp && exp < Date.now()) return null;
    const p = PERFIS[u.perfil] || { papel: u.perfil, area: '' };
    return Object.assign({}, u, p, { primeiroNome: u.nome.split(' ')[0] });
}

/** Limpa a sessao e volta para a tela de login. */
function sair() {
    ['token', 'usuario', 'expiraEm'].forEach(k => sessionStorage.removeItem(k));
    location.replace('login.html');
}

/** Troca de hash dispara o roteamento; se o hash ja for o mesmo, roteia direto. */
function irPara(hash) {
    if (location.hash === hash) rotear();
    else location.hash = hash;
}

// ---------- HTTP ----------

class ErroApi extends Error {
    constructor(status, mensagem, corpo) {
        super(mensagem);
        this.status = status;
        this.corpo = corpo || null;
    }
}

/** Mensagens amigaveis por status; `ctx` refina o texto (acao, catalogo, coordenador, usuario, solicitacao). */
function mensagemAmigavel(status, ctx) {
    const perfil = (usuarioAtual() || {}).perfil;
    switch (status) {
        case 400: return ctx === 'usuario'
            ? 'Dados inválidos. Confira o CPF, o e-mail institucional e a senha (mínimo de 8 caracteres).'
            : 'Dados inválidos. Confira os campos e tente novamente.';
        case 401: return 'Sessão expirada. Entre novamente.';
        case 403: return ctx === 'acao'
            ? (perfil === 'PROFESSOR'
                ? 'Você não coordena o curso desta solicitação, então não pode executar esta ação.'
                : 'Você não tem permissão para esta ação com o seu perfil.')
            : 'Acesso negado para o seu perfil.';
        case 404: return 'Registro não encontrado. Ele pode ter sido removido.';
        case 409:
            if (ctx === 'acao') return 'Esta ação não é permitida no status atual da solicitação. A tela foi atualizada.';
            if (ctx === 'catalogo') return 'Já existe um registro com este código.';
            if (ctx === 'coordenador') return 'Este professor já coordena o curso.';
            if (ctx === 'usuario') return 'CPF, e-mail ou matrícula já cadastrados.';
            return 'Operação não permitida no status atual da solicitação.';
        case 413: return 'Arquivo maior que o limite de 5 MB.';
        case 415: return 'Apenas arquivos PDF são aceitos.';
        case 422: return ctx === 'coordenador'
            ? 'Só é possível vincular um professor ativo.'
            : 'A disciplina destino não existe mais ou foi inativada. Escolha outra.';
        default: return 'Não foi possível concluir a operação (erro ' + status + ').';
    }
}

/** Chamada autenticada a qualquer endpoint; 401 devolve ao login. */
async function http(caminho, opcoes = {}) {
    const headers = Object.assign({ 'Authorization': 'Bearer ' + sessionStorage.getItem('token') }, opcoes.headers || {});
    let corpo = opcoes.body;
    if (opcoes.json !== undefined) {
        headers['Content-Type'] = 'application/json';
        corpo = JSON.stringify(opcoes.json);
    }
    const resp = await fetch(caminho, { method: opcoes.method || 'GET', headers, body: corpo });
    if (!resp.ok) {
        if (resp.status === 401) { sair(); throw new ErroApi(401, mensagemAmigavel(401)); }
        const corpoErro = await resp.json().catch(() => null);
        throw new ErroApi(resp.status, mensagemAmigavel(resp.status, opcoes.ctx || (opcoes.acao ? 'acao' : undefined)), corpoErro);
    }
    if (opcoes.blob) return resp.blob();
    const texto = await resp.text();
    return texto ? JSON.parse(texto) : null;
}

/** Atalho para /aproveitamentos. */
const api = (caminho, opcoes) => http(API + caminho, opcoes);

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
    if (!id) return 'Sistema';
    const u = usuarioAtual();
    return u && u.id === id ? `${u.nome} (${u.papel})` : 'Equipe do IF Sul de Minas';
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

/** Fila "Aguardando minha ação": só quem tem ação possível agora; estados finais nunca entram. */
function aguardaAcao(u, s) {
    if (STATUS_FINAIS.includes(s.status)) return false;
    if (u.perfil === 'PROFESSOR' && s.podeDecidir === false) return false;
    return acoesDisponiveis(u.perfil, s.status).length > 0;
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

function montarMoldura(u, chaveRota) {
    document.body.classList.toggle('sem-sessao', !u);
    if (!u) return;
    document.getElementById('area-usuario').textContent = u.area;
    document.getElementById('usuario-nome').textContent = u.nome;
    document.getElementById('usuario-papel').textContent = u.papel;
    document.getElementById('usuario-iniciais').textContent = iniciais(u.nome);
    document.getElementById('hoje').textContent = new Date().toLocaleDateString('pt-BR',
        { day: 'numeric', month: 'long', year: 'numeric' }) + ' • ' +
        new Date().toLocaleDateString('pt-BR', { weekday: 'long' });

    const itens = MENUS[u.perfil] || MENUS.ALUNO;
    document.getElementById('menu').innerHTML = itens.map(i => {
        const ativo = i.chave === chaveRota;
        return `<a href="${i.href}" class="${ativo ? 'ativo' : ''}"${ativo ? ' aria-current="page"' : ''}>${i.icone}<span>${esc(i.rotulo)}</span></a>`;
    }).join('');
}

function titulo(texto) {
    document.getElementById('titulo-pagina').textContent = texto;
    document.title = texto + ' · IF Conect';
}

// ---------- Roteamento ----------

const UUID_RE = '([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})';

/** Rotas por hash. As telas especificas de cada perfil ficam em js/aluno.js, js/secretaria.js e js/professor.js. */
const ROTAS = [
    { re: new RegExp('^/s/' + UUID_RE + '$', 'i'), chave: 'detalhe', tela: id => telaDetalhe(id) },
    { re: new RegExp('^/s/' + UUID_RE + '/editar$', 'i'), chave: 'detalhe', perfis: ['ALUNO'], tela: id => telaNova(id) },
    { re: /^\/nova$/, chave: 'nova', perfis: ['ALUNO'], tela: () => telaNova() },
    { re: /^\/cursos$/, chave: 'cursos', perfis: ['SERVIDOR'], tela: () => telaCursos() },
    { re: new RegExp('^/cursos/' + UUID_RE + '$', 'i'), chave: 'cursos', perfis: ['SERVIDOR'], tela: id => telaDisciplinas(id) },
    { re: /^\/coordenadores$/, chave: 'coordenadores', perfis: ['SERVIDOR'], tela: () => telaCoordenadores() },
    { re: /^\/usuarios$/, chave: 'usuarios', perfis: ['SERVIDOR'], tela: () => telaUsuarios() }
];

async function rotear() {
    clearTimeout(pollingParecer);
    clearTimeout(pollingAnexos);
    const u = usuarioAtual();
    if (!u) { montarMoldura(null); return sair(); }
    const hash = location.hash.replace(/^#/, '') || '/';
    let rota = null, args = [];
    for (const r of ROTAS) {
        const m = hash.match(r.re);
        if (m) { rota = r; args = m.slice(1); break; }
    }
    let avisoDaTela = null;
    if (rota && rota.perfis && !rota.perfis.includes(u.perfil)) {
        // Tela de outro perfil: mostra a tela inicial do usuario (sem novo hashchange, que apagaria o aviso).
        history.replaceState(null, '', '#/');
        rota = null;
        avisoDaTela = 'Esta área não está disponível para o seu perfil.';
    }
    montarMoldura(u, rota ? rota.chave : 'inicio');
    try {
        if (rota) await rota.tela(...args);
        else await telaLista();
        if (avisoDaTela) mostrarAviso(avisoDaTela);
    } catch (e) {
        if (rota && rota.chave === 'detalhe') navegar('#/', e instanceof ErroApi ? e.message : 'Falha de comunicação com o servidor.');
        else erro(e);
    }
}

/** Aviso a exibir depois da proxima troca de tela (a troca limpa avisos antigos). */
let avisoPendente = null;

function navegar(hash, mensagem, ok = false) {
    avisoPendente = mensagem ? [mensagem, ok] : null;
    if (location.hash === hash) { rotear().then(exibirAvisoPendente); return; }
    location.hash = hash;
}

function exibirAvisoPendente() {
    const pendente = avisoPendente;
    avisoPendente = null;
    if (pendente) mostrarAviso(...pendente);
}

window.addEventListener('hashchange', async () => {
    esconderAviso();
    // Captura antes de rotear: se a rota falhar e chamar navegar(), o aviso e do proximo hashchange.
    const pendente = avisoPendente;
    avisoPendente = null;
    await rotear();
    if (pendente) mostrarAviso(...pendente);
});
document.getElementById('btn-sair').onclick = sair;

// ---------- Lista (aluno: minhas solicitacoes; secretaria: fila; professor: para analisar) ----------

const FILTRO_ACAO = '__acao';

const TEXTOS_LISTA = {
    ALUNO: { titulo: 'Minhas solicitações', numero: 'Minhas solicitações', cartao: 'Solicitações' },
    SERVIDOR: { titulo: 'Fila de solicitações', numero: 'Solicitações no sistema', cartao: 'Fila' },
    PROFESSOR: { titulo: 'Para analisar', numero: 'Solicitações dos meus cursos', cartao: 'Solicitações dos cursos que coordeno' }
};

/** Filtros escolhidos na fila; lembrados enquanto a pagina estiver aberta. */
const filtrosLista = { status: FILTRO_ACAO, cursoId: '' };

async function telaLista() {
    const u = usuarioAtual();
    const txt = TEXTOS_LISTA[u.perfil] || TEXTOS_LISTA.ALUNO;
    const comFiltros = u.perfil !== 'ALUNO';
    titulo(txt.titulo);
    app.innerHTML = '<p class="muted carregando">Carregando solicitações</p>';

    const params = new URLSearchParams();
    if (comFiltros && filtrosLista.status && filtrosLista.status !== FILTRO_ACAO) params.set('status', filtrosLista.status);
    if (comFiltros && filtrosLista.cursoId) params.set('cursoId', filtrosLista.cursoId);
    const [todas, cursos] = await Promise.all([
        api(params.toString() ? '?' + params : ''),
        comFiltros ? http('/cursos') : Promise.resolve([])
    ]);
    todas.sort((a, b) => (b.atualizadoEm || '').localeCompare(a.atualizadoEm || ''));
    const aguardando = todas.filter(s => aguardaAcao(u, s)).length;
    const concluidas = todas.filter(s => STATUS_FINAIS.includes(s.status)).length;
    const lista = comFiltros && filtrosLista.status === FILTRO_ACAO
        ? todas.filter(s => aguardaAcao(u, s)) : todas;
    const agora = new Date();
    const mes = agora.toLocaleDateString('pt-BR', { month: 'long' });

    app.innerHTML = `
        <section class="boas-vindas">
            <p class="sobretitulo">${agora.getFullYear()} | ${esc(mes)}</p>
            <h2>Olá, ${esc(u.primeiroNome)}!</h2>
            <p>${aguardando === 0
                ? 'Nenhuma solicitação aguarda ação sua agora.'
                : `Você tem ${aguardando} ${aguardando === 1 ? 'solicitação que aguarda' : 'solicitações que aguardam'} ação sua.`}</p>
            ${u.perfil === 'ALUNO' ? `<div class="acoes"><a class="btn" href="#/nova">${ICONES.mais} Nova solicitação</a></div>` : ''}
        </section>

        <section class="numeros">
            <div class="numero"><span>${esc(txt.numero)}</span><strong>${todas.length}</strong></div>
            <div class="numero ${aguardando ? 'alerta' : ''}"><span>Aguardando sua ação</span><strong>${aguardando}</strong></div>
            <div class="numero realce"><span>Concluídas</span><strong>${concluidas}</strong></div>
        </section>

        <section class="card card-tabela">
            <div class="card-cab">
                <h3>${esc(txt.cartao)} <span class="contador">${lista.length}</span></h3>
                ${comFiltros ? `
                <div class="filtros">
                    <label>Status
                        <select id="filtro-status">
                            <option value="${FILTRO_ACAO}">Aguardando minha ação</option>
                            <option value="">Todos os status</option>
                            ${Object.keys(STATUS_ROTULO).map(k => `<option value="${k}">${esc(STATUS_ROTULO[k])}</option>`).join('')}
                        </select></label>
                    <label>Curso
                        <select id="filtro-curso">
                            <option value="">Todos os cursos</option>
                            ${cursos.map(c => `<option value="${esc(c.id)}">${esc(c.nome)}</option>`).join('')}
                        </select></label>
                </div>` : ''}
            </div>
            <div class="tabela-wrap">
                <table class="responsiva">
                    <thead><tr><th>Disciplina destino</th><th>Curso</th><th>Status</th><th>Atualizada em</th><th><span hidden>Ações</span></th></tr></thead>
                    <tbody>
                        ${lista.length === 0 ? '<tr><td colspan="5" class="vazio">Nenhuma solicitação encontrada.</td></tr>' : ''}
                        ${lista.map(s => linhaSolicitacao(u, s)).join('')}
                    </tbody>
                </table>
            </div>
        </section>`;

    if (comFiltros) {
        const fs = document.getElementById('filtro-status'), fc = document.getElementById('filtro-curso');
        fs.value = filtrosLista.status;
        fc.value = filtrosLista.cursoId;
        fs.onchange = () => { filtrosLista.status = fs.value; rotear(); };
        fc.onchange = () => { filtrosLista.cursoId = fc.value; rotear(); };
    }
    app.querySelectorAll('tr.clicavel').forEach(tr => {
        const abrir = () => { location.hash = '#/s/' + tr.dataset.id; };
        tr.onclick = abrir;
        tr.onkeydown = e => { if (e.key === 'Enter' && e.target === tr) abrir(); };
    });
    app.querySelectorAll('[data-rapida]').forEach(b => b.onclick = e => {
        e.stopPropagation();
        acaoRapida(b.closest('tr').dataset.id, b.dataset.rapida, b);
    });
}

function linhaSolicitacao(u, s) {
    const acoes = acoesDisponiveis(u.perfil, s.status);
    // Na fila da secretaria, conferir e devolver pendencia resolvem-se sem abrir o detalhe.
    const rapidas = u.perfil === 'SERVIDOR'
        ? acoes.filter(a => ['conferir', 'devolver-pendencia'].includes(a.acao)) : [];
    return `
        <tr class="clicavel" data-id="${esc(s.id)}" tabindex="0">
            <td data-rotulo="Disciplina"><strong>${esc(s.disciplinaDestino)}</strong>
                ${s.solicitacaoOrigemId ? '<span class="selo-nova">Nova tentativa</span>' : ''}
                ${aguardaAcao(u, s) ? '<span class="sub">Aguarda sua ação</span>' : ''}</td>
            <td data-rotulo="Curso">${esc(s.curso)}</td>
            <td data-rotulo="Status">${statusBadge(s.status)}</td>
            <td data-rotulo="Atualizada">${data(s.atualizadoEm)}</td>
            <td class="seta">${rapidas.length
                ? `<span class="acoes-linha">${rapidas.map(a => `<button type="button" class="btn btn-mini ${a.perigo ? 'btn-perigo' : ''}" data-rapida="${a.acao}">${esc(a.acao === 'conferir' ? 'Conferir' : 'Devolver')}</button>`).join('')}</span>`
                : ICONES.seta}</td>
        </tr>`;
}

/** Modal de observacao da acao; null = cancelou, '' = sem observacao (so nas opcionais). */
async function pedirObservacao(acao) {
    const cfg = {
        'devolver-pendencia': { titulo: 'Devolver para o aluno', rotulo: 'Motivo da pendência (o aluno verá no histórico)',
            obrigatorio: true, textoOk: 'Devolver', perigo: true },
        'indeferir': { titulo: 'Indeferir solicitação', rotulo: 'Motivo do indeferimento',
            obrigatorio: true, textoOk: 'Indeferir', perigo: true },
        'deferir': { titulo: 'Deferir solicitação', rotulo: 'Observação', obrigatorio: false, textoOk: 'Deferir' }
    }[acao];
    if (!cfg) return '';
    return modalTexto({ ...cfg, placeholder: 'Registrada no histórico junto com a ação', maxLength: 2000 });
}

async function acaoRapida(id, acao, botao) {
    let observacao = null;
    if (acao === 'devolver-pendencia') {
        observacao = await pedirObservacao(acao);
        if (observacao === null) return;
    }
    botao.disabled = true;
    try {
        const r = await api(`/${id}/${acao}`, { method: 'POST', json: observacao ? { observacao } : {}, acao: true });
        await telaLista();
        mostrarAviso(`Solicitação atualizada. Novo status: ${STATUS_ROTULO[r.status] || r.status}.`, true);
    } catch (e) {
        erro(e);
        if (e.status === 409 || e.status === 404) await telaLista().catch(() => {});
        else botao.disabled = false;
    }
}

// ---------- Detalhe ----------

async function telaDetalhe(id) {
    const u = usuarioAtual();
    titulo('Solicitação de aproveitamento');
    app.innerHTML = '<p class="muted carregando">Carregando solicitação</p>';
    const [s, historico] = await Promise.all([api('/' + id), api('/' + id + '/historico')]);
    // Professor so decide nas solicitacoes dos cursos que coordena (podeDecidir vem do backend).
    const acoes = u.perfil === 'PROFESSOR' && !s.podeDecidir ? [] : acoesDisponiveis(u.perfil, s.status);
    const podeEditar = u.perfil === 'ALUNO' && STATUS_ANEXO_EDITAVEL.includes(s.status);
    const podeAnexar = u.perfil === 'ALUNO' && STATUS_ANEXO_EDITAVEL.includes(s.status);
    const veParecer = u.perfil !== 'ALUNO';
    // SERVIDOR e professor veem o motivo da verificacao; reverificar: SERVIDOR ou professor coordenador.
    const ctxAnexos = { podeAnexar, veMotivo: u.perfil !== 'ALUNO',
        podeReverificar: u.perfil === 'SERVIDOR' || (u.perfil === 'PROFESSOR' && s.podeDecidir) };

    app.innerHTML = `
        <a class="voltar" href="#/">${ICONES.voltar} Voltar para a lista</a>
        ${blocoOrigem(s, u)}

        <section class="card">
            <div class="cabecalho-detalhe">
                <div>
                    <p class="sobretitulo">${esc(s.curso)} · ${esc(s.cargaHorariaDestino)} h</p>
                    <h2>${esc(s.disciplinaDestino)}</h2>
                    <span class="muted pequeno">Atualizada em ${data(s.atualizadoEm)}</span>
                </div>
                ${statusBadge(s.status)}
            </div>
            ${podeEditar ? `<div class="acoes" style="margin-top:1rem"><a class="btn btn-sec" href="#/s/${esc(id)}/editar">Editar</a></div>` : ''}
            ${blocoNovaTentativa(s, u)}
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
        ${veParecer ? blocoEmentas(s) : ''}

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
                ${veParecer ? '' : blocoCursadasAluno(s)}
                ${veParecer && s.preDiagnostico ? `
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
            <div id="lista-anexos">${listaAnexos(s.anexos, ctxAnexos)}</div>
            ${podeAnexar ? `
                <form id="form-anexo" class="acoes">
                    <input type="file" id="arquivo" accept="application/pdf,.pdf" required aria-label="Arquivo PDF" style="flex:1;min-width:200px">
                    <button type="submit" class="btn">Enviar PDF</button>
                </form>
                <p class="muted pequeno">Somente PDF, até 5 MB.</p>` : ''}
        </section>`;

    app.querySelectorAll('[data-acao]').forEach(b => b.onclick = () => executarAcao(id, b.dataset.acao, b, s));
    const btnNova = document.getElementById('solicitar-novamente');
    if (btnNova) btnNova.onclick = () => solicitarNovamente(id, btnNova);
    ligarAnexos(id);
    acompanharVerificacao(id, s.anexos, ctxAnexos);
    const formAnexo = document.getElementById('form-anexo');
    if (formAnexo) formAnexo.onsubmit = e => { e.preventDefault(); enviarAnexo(id, formAnexo); };
    if (veParecer) carregarParecer(id);
}

/** Aviso no topo: solicitacao que nasceu de um indeferimento (motivo + link para a original). */
function blocoOrigem(s, u) {
    if (!s.solicitacaoOrigemId) return '';
    const motivo = s.motivoIndeferimentoOrigem
        ? `<p>Decisão anterior (indeferida): “${esc(s.motivoIndeferimentoOrigem)}”</p>`
        : '<p class="muted">O indeferimento anterior não tem motivo registrado.</p>';
    const titulo = u.perfil === 'ALUNO' && s.status === 'RASCUNHO'
        ? 'Este rascunho é uma nova tentativa de uma solicitação indeferida.'
        : 'Nova tentativa de uma solicitação indeferida.';
    return `<section class="nota-origem"><span class="selo-nova">Nova tentativa</span>
        <strong>${titulo}</strong>${motivo}
        <a href="#/s/${esc(s.solicitacaoOrigemId)}">Ver solicitação original</a></section>`;
}

/** Aluno: "Solicitar novamente" na INDEFERIDA sem tentativa, ou link para a tentativa existente. */
function blocoNovaTentativa(s, u) {
    if (u.perfil !== 'ALUNO' || s.status !== 'INDEFERIDA') return '';
    if (s.novaTentativaId)
        return `<div class="acoes" style="margin-top:1rem"><a class="btn btn-sec" href="#/s/${esc(s.novaTentativaId)}">Ver nova tentativa</a></div>`;
    return '<div class="acoes" style="margin-top:1rem"><button type="button" class="btn" id="solicitar-novamente">Solicitar novamente</button></div>';
}

async function solicitarNovamente(id, botao) {
    const ok = await modalConfirmar({
        titulo: 'Solicitar novamente',
        mensagem: 'Um novo rascunho será criado com os mesmos dados (disciplina de destino e disciplinas cursadas). Os PDFs não são copiados: anexe de novo. Continuar?',
        textoOk: 'Criar rascunho'
    });
    if (!ok) return;
    botao.disabled = true;
    try {
        const nova = await api(`/${id}/nova-tentativa`, { method: 'POST', ctx: 'solicitacao' });
        navegar(`#/s/${nova.id}/editar`, 'Rascunho criado. Revise os dados, anexe o PDF e envie.', true);
    } catch (e) {
        if (e.status === 409 && e.corpo && e.corpo.novaTentativaId)
            return navegar('#/s/' + e.corpo.novaTentativaId, 'Esta solicitação já tem uma nova tentativa.');
        erro(e);
        botao.disabled = false;
    }
}

async function executarAcao(id, acao, botao, solicitacao) {
    const obs = document.getElementById('obs');
    let observacao = obs && obs.value.trim() ? obs.value.trim() : null;
    if (['devolver-pendencia', 'indeferir', 'deferir'].includes(acao)) {
        const texto = await pedirObservacao(acao);
        if (texto === null) return;
        observacao = texto || null;
    }
    if (acao === 'enviar' && solicitacao.anexos.length === 0
        && !(await modalConfirmar({ titulo: 'Enviar sem PDF', mensagem: 'Nenhum PDF foi anexado. Enviar a solicitação mesmo assim?', textoOk: 'Enviar' }))) return;
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

const SELO_ANEXO = {
    VERIFICANDO: { rotulo: 'Verificando…', classe: 'st-ANALISE' },
    VALIDO: { rotulo: 'Válido', classe: 'st-DEFERIDA' },
    INVALIDO: { rotulo: 'Inválido', classe: 'st-INDEFERIDA' },
    PRECISA_CONFERIR: { rotulo: 'Precisa conferir', classe: 'st-PENDENTE' },
};
const TIPO_ANEXO = { EMENTA: 'Ementa', HISTORICO: 'Histórico', OUTRO: 'Outro documento' };

function seloAnexo(a) {
    const selo = SELO_ANEXO[a.statusVerificacao] || SELO_ANEXO.PRECISA_CONFERIR;
    return `<span class="status ${selo.classe}">${esc(selo.rotulo)}</span>`;
}

/** Lista de anexos com selo de verificacao; motivo so para SERVIDOR/professor, aviso so para o aluno. */
function listaAnexos(anexos, ctx) {
    if (!anexos.length) return '<p class="muted">Nenhum anexo.</p>';
    return `<ul class="anexos">${anexos.map(a => `
        <li>
            <span class="anexo-nome">${ICONES.pdf} <span>${esc(a.nomeArquivo)}
                <span class="muted pequeno">· ${tamanho(a.tamanho)} · ${data(a.criadoEm)}</span></span></span>
            <span class="acoes-anexo">
                ${seloAnexo(a)}
                <button type="button" class="btn btn-sec btn-mini" data-baixar="${esc(a.id)}" data-nome="${esc(a.nomeArquivo)}">Baixar</button>
                ${ctx.podeReverificar ? `<button type="button" class="btn btn-sec btn-mini" data-reverificar="${esc(a.id)}">Reverificar</button>` : ''}
                ${ctx.podeAnexar ? `<button type="button" class="btn btn-perigo btn-mini" data-remover="${esc(a.id)}">Remover</button>` : ''}
            </span>
            ${ctx.veMotivo && a.motivoVerificacao ? `<p class="anexo-motivo pequeno muted">${a.tipoDetectado ? esc(TIPO_ANEXO[a.tipoDetectado] || a.tipoDetectado) + ' · ' : ''}${esc(a.motivoVerificacao)}</p>` : ''}
            ${!ctx.veMotivo && a.statusVerificacao === 'INVALIDO' ? '<p class="anexo-motivo pequeno aviso-anexo">Revise o anexo.</p>' : ''}
        </li>`).join('')}</ul>`;
}

function ligarAnexos(id) {
    app.querySelectorAll('[data-baixar]').forEach(b => b.onclick = () => baixarAnexo(id, b.dataset.baixar, b.dataset.nome));
    app.querySelectorAll('[data-remover]').forEach(b => b.onclick = () => removerAnexo(id, b.dataset.remover));
    app.querySelectorAll('[data-reverificar]').forEach(b => b.onclick = () => reverificarAnexo(id, b.dataset.reverificar, b));
}

/** Enquanto algum anexo estiver VERIFICANDO, atualiza so a lista de anexos a cada 3 s (ate ~1 min). */
function acompanharVerificacao(id, anexos, ctx, tentativas = 0) {
    clearTimeout(pollingAnexos);
    if (!anexos.some(a => a.statusVerificacao === 'VERIFICANDO') || tentativas >= 20) return;
    pollingAnexos = setTimeout(async () => {
        const lista = document.getElementById('lista-anexos');
        if (!lista) return; // saiu da tela
        try {
            const atual = (await api('/' + id)).anexos;
            const aindaLa = document.getElementById('lista-anexos');
            if (!aindaLa) return;
            aindaLa.innerHTML = listaAnexos(atual, ctx);
            ligarAnexos(id);
            acompanharVerificacao(id, atual, ctx, tentativas + 1);
        } catch (e) { /* tenta de novo no proximo ciclo */ acompanharVerificacao(id, anexos, ctx, tentativas + 1); }
    }, 3000);
}

async function reverificarAnexo(id, anexoId, botao) {
    botao.disabled = true;
    try {
        await api(`/${id}/anexos/${anexoId}/reverificar`, { method: 'POST', acao: true });
        await telaDetalhe(id);
        mostrarAviso('Verificação solicitada. O resultado aparece em alguns segundos.', true);
    } catch (e) {
        erro(e);
        botao.disabled = false;
    }
}

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
    if (!(await modalConfirmar({ titulo: 'Remover anexo', mensagem: 'Remover este anexo?', textoOk: 'Remover', perigo: true }))) return;
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
    card.className = 'card destaque parecer';
    if (!p) {
        corpo = '<p class="muted">Ainda não há parecer. Ele é gerado automaticamente quando o aluno envia a solicitação.</p>';
    } else if (p.status === 'PROCESSANDO') {
        corpo = '<p class="muted carregando">A IA está analisando a solicitação</p>';
    } else if (p.status === 'FALHOU') {
        corpo = `<p class="rec rec-SUGERE_INDEFERIR">Falha ao gerar o parecer</p>
                 ${p.erro ? `<p class="muted pequeno">${esc(p.erro)}</p>` : ''}`;
    } else {
        const sim = Math.max(0, Math.min(100, p.similaridade ?? 0));
        const tom = RECOMENDACAO_TOM[p.recomendacao] || "neutro";
        const origem = !p.modelo ? "" : p.modelo.startsWith("heuristica") ? "heurística local" : `gerado por ${p.modelo}`;
        card.className = `card destaque parecer parecer-${tom}`;
        corpo = `
            <span class="rec rec-${esc(p.recomendacao)}">${esc(RECOMENDACAO_ROTULO[p.recomendacao] || p.recomendacao || '—')}</span>
            <div class="pequeno">Similaridade de conteúdo: <strong>${sim}%</strong></div>
            <div class="barra" role="progressbar" aria-valuenow="${sim}" aria-valuemin="0" aria-valuemax="100" aria-label="Similaridade"><span style="width:${sim}%"></span></div>
            <p>${esc(p.justificativa || '')}</p>
            <p class="muted pequeno" style="margin-bottom:0">Gerado em ${data(p.geradoEm)}. Sugestão de apoio: a decisão é do coordenador.</p>
            ${origem ? `<p class="muted rodape-modelo">${esc(origem)}</p>` : ""}`;
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
