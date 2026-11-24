'use strict';

/* Login e cadastro de aluno. O JWT vai para sessionStorage; app.js o envia como Bearer. */

const $ = id => document.getElementById(id);
const aviso = $('aviso');
const formLogin = $('form-login');
const formCadastro = $('form-cadastro');

if (sessionStorage.getItem('token')) location.replace('aproveitamento.html');

function mostrarAviso(msg, ok = false) {
    aviso.className = 'aviso' + (ok ? ' ok' : '');
    aviso.textContent = msg;
    aviso.hidden = false;
}

function mascaraCpf(input) {
    input.addEventListener('input', () => {
        const d = input.value.replace(/\D/g, '').slice(0, 11);
        input.value = d.replace(/^(\d{3})(\d)/, '$1.$2').replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3')
            .replace(/\.(\d{3})(\d)/, '.$1-$2');
    });
}
mascaraCpf($('login-cpf'));
mascaraCpf($('cad-cpf'));

function alternar(cadastro) {
    formLogin.hidden = cadastro;
    formCadastro.hidden = !cadastro;
    aviso.hidden = true;
}
$('ir-cadastro').onclick = e => { e.preventDefault(); alternar(true); };
$('ir-login').onclick = e => { e.preventDefault(); alternar(false); };
if (location.hash === '#cadastro') alternar(true);

async function post(caminho, corpo) {
    const resp = await fetch(caminho, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(corpo)
    });
    const texto = await resp.text();
    let dados = null;
    try { dados = texto ? JSON.parse(texto) : null; } catch (e) { /* corpo nao JSON */ }
    return { ok: resp.ok, status: resp.status, dados };
}

const dados = form => Object.fromEntries(new FormData(form).entries());

async function entrar(cpf, senha, botao) {
    botao.disabled = true;
    try {
        const r = await post('/auth/login', { cpf, senha });
        if (r.status === 401) return mostrarAviso('CPF ou senha inválidos.');
        if (!r.ok) return mostrarAviso('Não foi possível entrar (erro ' + r.status + ').');
        sessionStorage.setItem('token', r.dados.token);
        sessionStorage.setItem('expiraEm', r.dados.expiraEm);
        sessionStorage.setItem('usuario', JSON.stringify(r.dados.usuario));
        location.replace('aproveitamento.html');
    } catch (e) {
        mostrarAviso('Falha de comunicação com o servidor.');
    } finally {
        botao.disabled = false;
    }
}

formLogin.onsubmit = e => {
    e.preventDefault();
    const d = dados(formLogin);
    if (!d.cpf || !d.senha) return mostrarAviso('Informe CPF e senha.');
    entrar(d.cpf, d.senha, formLogin.querySelector('[type=submit]'));
};

formCadastro.onsubmit = async e => {
    e.preventDefault();
    const d = dados(formCadastro);
    if (d.senha.length < 8) return mostrarAviso('A senha deve ter ao menos 8 caracteres.');
    const botao = formCadastro.querySelector('[type=submit]');
    botao.disabled = true;
    try {
        const r = await post('/auth/cadastro', d);
        if (r.status === 409) return mostrarAviso('CPF, e-mail ou matrícula já cadastrados.');
        if (r.status === 400) return mostrarAviso('Dados inválidos. Confira CPF, e-mail institucional e senha.');
        if (!r.ok) return mostrarAviso('Não foi possível criar a conta (erro ' + r.status + ').');
        botao.disabled = false;
        await entrar(d.cpf, d.senha, botao);
    } catch (err) {
        mostrarAviso('Falha de comunicação com o servidor.');
    } finally {
        botao.disabled = false;
    }
};
