package ifsuldeminas.edu.br.ifsystem.auth.service;

import ifsuldeminas.edu.br.ifsystem.usuario.domain.Perfil;

import java.util.UUID;

/** Principal colocado no SecurityContext apos a validacao do token. */
public record UsuarioAutenticado(UUID id, Perfil perfil, String nome) {
}
