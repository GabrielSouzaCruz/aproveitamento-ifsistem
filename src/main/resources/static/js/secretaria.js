'use strict';

/* Telas da secretaria (SERVIDOR): cursos e disciplinas, coordenadores e usuarios. */

function seloAtivo(ativo) {
    return `<span class="status ${ativo ? 'st-DEFERIDA' : 'st-INDEFERIDA'}">${ativo ? 'Ativo' : 'Inativo'}</span>`;
}

function cpfMascarado(cpf) {
    const d = String(cpf || '').replace(/\D/g, '');
    return d.length === 11 ? d.replace(/^(\d{3})(\d{3})(\d{3})(\d{2})$/, '$1.$2.$3-$4') : (cpf || '—');
}

/** Executa uma chamada de escrita, avisa o resultado e recarrega a tela; em erro, so avisa. */
async function escrever(chamada, mensagemOk, recarregar) {
    try {
        await chamada();
    } catch (e) {
        erro(e);
        return false;
    }
    await recarregar();
    mostrarAviso(mensagemOk, true);
    return true;
}

// ---------- Cursos ----------

async function telaCursos() {
    titulo('Cursos e disciplinas');
    app.innerHTML = '<p class="muted carregando">Carregando cursos</p>';
    const cursos = await http('/cursos');
    let editando = null;

    app.innerHTML = `
        <section class="card card-tabela">
            <div class="card-cab"><h3>Cursos <span class="contador">${cursos.length}</span></h3></div>
            <div class="tabela-wrap">
                <table class="responsiva">
                    <thead><tr><th>Código</th><th>Nome</th><th>Situação</th><th>Ações</th></tr></thead>
                    <tbody>
                        ${cursos.length === 0 ? '<tr><td colspan="4" class="vazio">Nenhum curso cadastrado.</td></tr>' : ''}
                        ${cursos.map(c => `
                            <tr>
                                <td data-rotulo="Código">${esc(c.codigo)}</td>
                                <td data-rotulo="Nome"><strong>${esc(c.nome)}</strong></td>
                                <td data-rotulo="Situação">${seloAtivo(c.ativo)}</td>
                                <td class="celula-acoes"><span class="acoes-linha">
                                    <a class="btn btn-mini" href="#/cursos/${esc(c.id)}">Disciplinas</a>
                                    <button type="button" class="btn btn-sec btn-mini" data-editar="${esc(c.id)}">Editar</button>
                                    <button type="button" class="btn btn-mini ${c.ativo ? 'btn-perigo' : 'btn-sec'}" data-ativo="${esc(c.id)}" data-novo="${!c.ativo}">${c.ativo ? 'Inativar' : 'Ativar'}</button>
                                </span></td>
                            </tr>`).join('')}
                    </tbody>
                </table>
            </div>
        </section>

        <form id="form-curso" class="card" style="margin-top:1.25rem">
            <h3 id="titulo-form-curso">Novo curso</h3>
            <div class="campos-curso">
                <div class="campo"><label for="curso-codigo">Código</label><input id="curso-codigo" required maxlength="50" placeholder="Ex.: TADS"></div>
                <div class="campo"><label for="curso-nome">Nome</label><input id="curso-nome" required maxlength="255" placeholder="Ex.: Tecnologia em Análise e Desenvolvimento de Sistemas"></div>
            </div>
            <div class="acoes">
                <button type="submit" class="btn" id="salvar-curso">Cadastrar curso</button>
                <button type="button" class="btn btn-sec" id="cancelar-curso" hidden>Cancelar edição</button>
            </div>
        </form>`;

    const form = document.getElementById('form-curso');
    const codigo = document.getElementById('curso-codigo'), nome = document.getElementById('curso-nome');
    const sairDaEdicao = () => {
        editando = null;
        form.reset();
        document.getElementById('titulo-form-curso').textContent = 'Novo curso';
        document.getElementById('salvar-curso').textContent = 'Cadastrar curso';
        document.getElementById('cancelar-curso').hidden = true;
    };
    document.getElementById('cancelar-curso').onclick = sairDaEdicao;

    app.querySelectorAll('[data-editar]').forEach(b => b.onclick = async () => {
        const c = cursos.find(x => x.id === b.dataset.editar);
        editando = c.id;
        codigo.value = c.codigo;
        nome.value = c.nome;
        document.getElementById('titulo-form-curso').textContent = 'Editar curso';
        document.getElementById('salvar-curso').textContent = 'Salvar alterações';
        document.getElementById('cancelar-curso').hidden = false;
        form.scrollIntoView({ behavior: 'smooth' });
        nome.focus();
    });
    app.querySelectorAll('[data-ativo]').forEach(b => b.onclick = async () => {
        const ativar = b.dataset.novo === 'true';
        if (!ativar && !(await modalConfirmar({ titulo: 'Confirmar', mensagem: 'Inativar este curso? Ele deixa de aparecer para novas solicitações.', textoOk: 'Inativar', perigo: true }))) return;
        b.disabled = true;
        escrever(() => http(`/cursos/${b.dataset.ativo}/${ativar ? 'ativar' : 'inativar'}`, { method: 'POST', ctx: 'catalogo' }),
            ativar ? 'Curso ativado.' : 'Curso inativado.', telaCursos).then(ok => { if (!ok) b.disabled = false; });
    });
    form.onsubmit = e => {
        e.preventDefault();
        const corpo = { codigo: codigo.value.trim(), nome: nome.value.trim() };
        const botao = document.getElementById('salvar-curso');
        botao.disabled = true;
        const edicao = editando;
        escrever(() => edicao
                ? http(`/cursos/${edicao}`, { method: 'PUT', json: corpo, ctx: 'catalogo' })
                : http('/cursos', { method: 'POST', json: corpo, ctx: 'catalogo' }),
            edicao ? 'Curso atualizado.' : 'Curso cadastrado.', telaCursos).then(ok => { if (!ok) botao.disabled = false; });
    };
}

// ---------- Disciplinas de um curso ----------

async function telaDisciplinas(cursoId) {
    titulo('Disciplinas do curso');
    app.innerHTML = '<p class="muted carregando">Carregando disciplinas</p>';
    const [curso, disciplinas] = await Promise.all([http(`/cursos/${cursoId}`), http(`/cursos/${cursoId}/disciplinas`)]);
    let editando = null;

    app.innerHTML = `
        <a class="voltar" href="#/cursos">${ICONES.voltar} Voltar para os cursos</a>
        <section class="card">
            <p class="sobretitulo">${esc(curso.codigo)}</p>
            <h2>${esc(curso.nome)} ${seloAtivo(curso.ativo)}</h2>
        </section>

        <section class="card card-tabela">
            <div class="card-cab"><h3>Disciplinas <span class="contador">${disciplinas.length}</span></h3></div>
            <div class="tabela-wrap">
                <table class="responsiva">
                    <thead><tr><th>Código</th><th>Disciplina</th><th>Carga</th><th>Situação</th><th>Ações</th></tr></thead>
                    <tbody>
                        ${disciplinas.length === 0 ? '<tr><td colspan="5" class="vazio">Nenhuma disciplina cadastrada neste curso.</td></tr>' : ''}
                        ${disciplinas.map(d => `
                            <tr>
                                <td data-rotulo="Código">${esc(d.codigo)}</td>
                                <td data-rotulo="Disciplina"><strong>${esc(d.nome)}</strong>
                                    <details class="cursada"><summary>Ver ementa</summary><p class="ementa">${esc(d.ementa)}</p></details></td>
                                <td data-rotulo="Carga">${esc(d.cargaHoraria)} h</td>
                                <td data-rotulo="Situação">${seloAtivo(d.ativo)}</td>
                                <td class="celula-acoes"><span class="acoes-linha">
                                    <button type="button" class="btn btn-sec btn-mini" data-editar="${esc(d.id)}">Editar</button>
                                    <button type="button" class="btn btn-mini ${d.ativo ? 'btn-perigo' : 'btn-sec'}" data-ativo="${esc(d.id)}" data-novo="${!d.ativo}">${d.ativo ? 'Inativar' : 'Ativar'}</button>
                                </span></td>
                            </tr>`).join('')}
                    </tbody>
                </table>
            </div>
        </section>

        <form id="form-disc" class="card" style="margin-top:1.25rem">
            <h3 id="titulo-form-disc">Nova disciplina</h3>
            <div class="campos-disc">
                <div class="campo"><label for="disc-codigo">Código</label><input id="disc-codigo" required maxlength="50" placeholder="Ex.: TADS201"></div>
                <div class="campo"><label for="disc-nome">Nome</label><input id="disc-nome" required maxlength="255" placeholder="Ex.: Banco de Dados"></div>
                <div class="campo"><label for="disc-carga">Carga (h)</label><input id="disc-carga" type="number" min="1" required></div>
            </div>
            <div class="campo"><label for="disc-ementa">Ementa</label>
                <textarea id="disc-ementa" rows="6" required placeholder="Conteúdo programático da disciplina. O parecer da IA compara este texto com a ementa cursada pelo aluno."></textarea></div>
            <div class="acoes">
                <button type="submit" class="btn" id="salvar-disc">Cadastrar disciplina</button>
                <button type="button" class="btn btn-sec" id="cancelar-disc" hidden>Cancelar edição</button>
            </div>
        </form>`;

    const form = document.getElementById('form-disc');
    const campo = id => document.getElementById(id);
    const sairDaEdicao = () => {
        editando = null;
        form.reset();
        campo('titulo-form-disc').textContent = 'Nova disciplina';
        campo('salvar-disc').textContent = 'Cadastrar disciplina';
        campo('cancelar-disc').hidden = true;
    };
    campo('cancelar-disc').onclick = sairDaEdicao;

    app.querySelectorAll('[data-editar]').forEach(b => b.onclick = async () => {
        const d = disciplinas.find(x => x.id === b.dataset.editar);
        editando = d.id;
        campo('disc-codigo').value = d.codigo;
        campo('disc-nome').value = d.nome;
        campo('disc-carga').value = d.cargaHoraria;
        campo('disc-ementa').value = d.ementa;
        campo('titulo-form-disc').textContent = 'Editar disciplina';
        campo('salvar-disc').textContent = 'Salvar alterações';
        campo('cancelar-disc').hidden = false;
        form.scrollIntoView({ behavior: 'smooth' });
        campo('disc-nome').focus();
    });
    app.querySelectorAll('[data-ativo]').forEach(b => b.onclick = async () => {
        const ativar = b.dataset.novo === 'true';
        if (!ativar && !(await modalConfirmar({ titulo: 'Confirmar', mensagem: 'Inativar esta disciplina? Ela deixa de aparecer para novas solicitações.', textoOk: 'Inativar', perigo: true }))) return;
        b.disabled = true;
        escrever(() => http(`/cursos/${cursoId}/disciplinas/${b.dataset.ativo}/${ativar ? 'ativar' : 'inativar'}`,
                { method: 'POST', ctx: 'catalogo' }),
            ativar ? 'Disciplina ativada.' : 'Disciplina inativada.', () => telaDisciplinas(cursoId))
            .then(ok => { if (!ok) b.disabled = false; });
    });
    form.onsubmit = e => {
        e.preventDefault();
        const corpo = {
            codigo: campo('disc-codigo').value.trim(),
            nome: campo('disc-nome').value.trim(),
            cargaHoraria: Number(campo('disc-carga').value),
            ementa: campo('disc-ementa').value.trim()
        };
        const botao = campo('salvar-disc');
        botao.disabled = true;
        const edicao = editando;
        escrever(() => edicao
                ? http(`/cursos/${cursoId}/disciplinas/${edicao}`, { method: 'PUT', json: corpo, ctx: 'catalogo' })
                : http(`/cursos/${cursoId}/disciplinas`, { method: 'POST', json: corpo, ctx: 'catalogo' }),
            edicao ? 'Disciplina atualizada.' : 'Disciplina cadastrada.', () => telaDisciplinas(cursoId))
            .then(ok => { if (!ok) botao.disabled = false; });
    };
}

// ---------- Coordenadores ----------

let cursoCoordSelecionado = '';

async function telaCoordenadores() {
    titulo('Coordenadores');
    app.innerHTML = '<p class="muted carregando">Carregando</p>';
    const [cursos, professores] = await Promise.all([http('/cursos'), http('/usuarios?perfil=PROFESSOR')]);
    if (!cursos.some(c => c.id === cursoCoordSelecionado)) cursoCoordSelecionado = cursos.length ? cursos[0].id : '';
    const coordenadores = cursoCoordSelecionado ? await http(`/cursos/${cursoCoordSelecionado}/coordenadores`) : [];
    const porId = new Map(professores.map(p => [p.id, p]));
    const vinculados = new Set(coordenadores.map(c => c.professorId));
    const candidatos = professores.filter(p => p.ativo && !vinculados.has(p.id));

    app.innerHTML = `
        <section class="card">
            <div class="campo" style="margin-bottom:0">
                <label for="sel-curso">Curso</label>
                <select id="sel-curso">
                    ${cursos.length === 0 ? '<option value="">Nenhum curso cadastrado</option>' : ''}
                    ${cursos.map(c => `<option value="${esc(c.id)}">${esc(c.nome)}${c.ativo ? '' : ' (inativo)'}</option>`).join('')}
                </select>
            </div>
        </section>

        <section class="card card-tabela">
            <div class="card-cab"><h3>Coordenadores do curso <span class="contador">${coordenadores.length}</span></h3></div>
            <div class="tabela-wrap">
                <table class="responsiva">
                    <thead><tr><th>Professor</th><th>E-mail</th><th>Situação</th><th><span hidden>Ações</span></th></tr></thead>
                    <tbody>
                        ${coordenadores.length === 0 ? '<tr><td colspan="4" class="vazio">Nenhum professor coordena este curso.</td></tr>' : ''}
                        ${coordenadores.map(c => {
                            const p = porId.get(c.professorId);
                            return `<tr>
                                <td data-rotulo="Professor"><strong>${esc(p ? p.nome : 'Professor não encontrado')}</strong></td>
                                <td data-rotulo="E-mail">${esc(p ? p.email : '—')}</td>
                                <td data-rotulo="Situação">${p ? seloAtivo(p.ativo) : '—'}</td>
                                <td class="celula-acoes"><button type="button" class="btn btn-perigo btn-mini" data-desvincular="${esc(c.professorId)}">Desvincular</button></td>
                            </tr>`;
                        }).join('')}
                    </tbody>
                </table>
            </div>
        </section>

        <form id="form-coord" class="card" style="margin-top:1.25rem">
            <h3>Vincular professor ao curso</h3>
            <div class="campo"><label for="sel-prof">Professor (ativo)</label>
                <select id="sel-prof" required ${candidatos.length && cursoCoordSelecionado ? '' : 'disabled'}>
                    ${candidatos.length
                        ? '<option value="">Selecione o professor</option>' + candidatos.map(p => `<option value="${esc(p.id)}">${esc(p.nome)} · ${esc(p.email)}</option>`).join('')
                        : '<option value="">Nenhum professor ativo disponível para vincular</option>'}
                </select></div>
            <button type="submit" class="btn" ${candidatos.length && cursoCoordSelecionado ? '' : 'disabled'}>Vincular</button>
            ${professores.length === 0 ? '<p class="muted pequeno">Cadastre professores na tela "Usuários".</p>' : ''}
        </form>`;

    const sel = document.getElementById('sel-curso');
    sel.value = cursoCoordSelecionado;
    sel.onchange = () => { cursoCoordSelecionado = sel.value; telaCoordenadores().catch(erro); };
    app.querySelectorAll('[data-desvincular]').forEach(b => b.onclick = async () => {
        if (!(await modalConfirmar({ titulo: 'Confirmar', mensagem: 'Desvincular este professor da coordenação do curso?', textoOk: 'Desvincular', perigo: true }))) return;
        b.disabled = true;
        escrever(() => http(`/cursos/${cursoCoordSelecionado}/coordenadores`,
                { method: 'DELETE', json: { professorId: b.dataset.desvincular }, ctx: 'coordenador' }),
            'Professor desvinculado do curso.', telaCoordenadores).then(ok => { if (!ok) b.disabled = false; });
    });
    document.getElementById('form-coord').onsubmit = e => {
        e.preventDefault();
        const botao = e.target.querySelector('[type=submit]');
        botao.disabled = true;
        escrever(() => http(`/cursos/${cursoCoordSelecionado}/coordenadores`,
                { method: 'POST', json: { professorId: document.getElementById('sel-prof').value }, ctx: 'coordenador' }),
            'Professor vinculado ao curso.', telaCoordenadores).then(ok => { if (!ok) botao.disabled = false; });
    };
}

// ---------- Usuarios ----------

let filtroPerfilUsuarios = '';

const PERFIL_ROTULO = { ALUNO: 'Aluno', PROFESSOR: 'Professor', SERVIDOR: 'Servidor (secretaria)' };

async function telaUsuarios() {
    titulo('Usuários');
    app.innerHTML = '<p class="muted carregando">Carregando usuários</p>';
    const eu = usuarioAtual();
    const usuarios = await http('/usuarios' + (filtroPerfilUsuarios ? '?perfil=' + filtroPerfilUsuarios : ''));
    usuarios.sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'));

    app.innerHTML = `
        <section class="card card-tabela">
            <div class="card-cab">
                <h3>Usuários <span class="contador">${usuarios.length}</span></h3>
                <div class="filtros">
                    <label>Perfil
                        <select id="filtro-perfil">
                            <option value="">Todos</option>
                            ${Object.keys(PERFIL_ROTULO).map(k => `<option value="${k}">${esc(PERFIL_ROTULO[k])}</option>`).join('')}
                        </select></label>
                </div>
            </div>
            <div class="tabela-wrap">
                <table class="responsiva">
                    <thead><tr><th>Nome</th><th>CPF</th><th>E-mail</th><th>Perfil</th><th>Situação</th><th><span hidden>Ações</span></th></tr></thead>
                    <tbody>
                        ${usuarios.length === 0 ? '<tr><td colspan="6" class="vazio">Nenhum usuário encontrado.</td></tr>' : ''}
                        ${usuarios.map(x => `
                            <tr>
                                <td data-rotulo="Nome"><strong>${esc(x.nome)}</strong></td>
                                <td data-rotulo="CPF">${esc(cpfMascarado(x.cpf))}</td>
                                <td data-rotulo="E-mail">${esc(x.email)}</td>
                                <td data-rotulo="Perfil">${esc(PERFIL_ROTULO[x.perfil] || x.perfil)}</td>
                                <td data-rotulo="Situação">${seloAtivo(x.ativo)}</td>
                                <td class="celula-acoes">${x.id === eu.id ? '<span class="muted pequeno">Você</span>'
                                    : `<button type="button" class="btn btn-mini ${x.ativo ? 'btn-perigo' : 'btn-sec'}" data-ativo="${esc(x.id)}" data-novo="${!x.ativo}">${x.ativo ? 'Inativar' : 'Ativar'}</button>`}</td>
                            </tr>`).join('')}
                    </tbody>
                </table>
            </div>
        </section>

        <form id="form-usuario" class="card" style="margin-top:1.25rem" autocomplete="off">
            <h3>Novo professor ou servidor</h3>
            <div class="campos-2 campos-usuario">
                <div class="campo"><label for="u-nome">Nome completo</label><input id="u-nome" required></div>
                <div class="campo"><label for="u-cpf">CPF</label><input id="u-cpf" required inputmode="numeric" placeholder="000.000.000-00"></div>
                <div class="campo"><label for="u-perfil">Perfil</label>
                    <select id="u-perfil" required>
                        <option value="PROFESSOR">Professor</option>
                        <option value="SERVIDOR">Servidor (secretaria)</option>
                    </select></div>
            </div>
            <div class="campos-2 campos-usuario-2">
                <div class="campo"><label for="u-email">E-mail institucional</label><input id="u-email" type="email" required placeholder="nome@ifsuldeminas.edu.br"></div>
                <div class="campo"><label for="u-senha">Senha inicial</label><input id="u-senha" type="password" required minlength="8" autocomplete="new-password"></div>
            </div>
            <button type="submit" class="btn">Criar usuário</button>
            <p class="muted pequeno">Alunos se cadastram sozinhos na tela de login. A senha deve ter ao menos 8 caracteres.</p>
        </form>`;

    const filtro = document.getElementById('filtro-perfil');
    filtro.value = filtroPerfilUsuarios;
    filtro.onchange = () => { filtroPerfilUsuarios = filtro.value; telaUsuarios().catch(erro); };

    const cpf = document.getElementById('u-cpf');
    cpf.addEventListener('input', () => {
        const d = cpf.value.replace(/\D/g, '').slice(0, 11);
        cpf.value = d.replace(/^(\d{3})(\d)/, '$1.$2').replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3')
            .replace(/\.(\d{3})(\d)/, '.$1-$2');
    });

    app.querySelectorAll('[data-ativo]').forEach(b => b.onclick = async () => {
        const ativar = b.dataset.novo === 'true';
        if (!ativar && !(await modalConfirmar({ titulo: 'Confirmar', mensagem: 'Inativar este usuário? Ele não conseguirá mais entrar no sistema.', textoOk: 'Inativar', perigo: true }))) return;
        b.disabled = true;
        escrever(() => http(`/usuarios/${b.dataset.ativo}/ativo`, { method: 'PATCH', json: { ativo: ativar }, ctx: 'usuario' }),
            ativar ? 'Usuário ativado.' : 'Usuário inativado.', telaUsuarios).then(ok => { if (!ok) b.disabled = false; });
    });
    document.getElementById('form-usuario').onsubmit = e => {
        e.preventDefault();
        const v = id => document.getElementById(id).value.trim();
        const botao = e.target.querySelector('[type=submit]');
        botao.disabled = true;
        escrever(() => http('/usuarios', { method: 'POST', ctx: 'usuario', json: {
                nome: v('u-nome'), cpf: v('u-cpf'), email: v('u-email'),
                senha: document.getElementById('u-senha').value, perfil: v('u-perfil')
            } }),
            'Usuário criado.', telaUsuarios).then(ok => { if (!ok) botao.disabled = false; });
    };
}
