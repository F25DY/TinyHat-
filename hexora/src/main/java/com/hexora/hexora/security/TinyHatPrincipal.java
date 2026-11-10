package com.hexora.hexora.security;

import com.hexora.hexora.model.Usuario;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class TinyHatPrincipal implements UserDetails, Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private final Long userId;
	private final String publicId;
	private final Long cuentaId;
	private final Long organizacionId;
	private final String rol;
	private final String estado;
	private final String email;
	private final String passwordHash;
	private final boolean cuentaActiva;

	public TinyHatPrincipal(Usuario usuario, boolean cuentaActiva) {
		this.userId = Objects.requireNonNull(usuario.getId(), "User id is required");
		this.publicId = Objects.requireNonNull(usuario.getPublicId(), "Public id is required");
		this.cuentaId = Objects.requireNonNull(usuario.getCuentaId(), "Account id is required");
		this.organizacionId = usuario.getOrganizacionId();
		this.rol = Objects.requireNonNull(usuario.getRol(), "Role is required");
		this.estado = Objects.requireNonNull(usuario.getEstado(), "Status is required");
		this.email = Objects.requireNonNull(usuario.getEmailNormalizado(), "Email is required");
		this.passwordHash = Objects.requireNonNull(usuario.getPasswordHash(), "Password hash is required");
		this.cuentaActiva = cuentaActiva;
	}

	public Long getUserId() {
		return userId;
	}

	public String getPublicId() {
		return publicId;
	}

	public Long getCuentaId() {
		return cuentaId;
	}

	public Long getOrganizacionId() {
		return organizacionId;
	}

	public String getRol() {
		return rol;
	}

	public String getEstado() {
		return estado;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		if (!"AUDITOR".equals(rol) && !"CLIENTE_ADMIN".equals(rol)) {
			return List.of();
		}
		return List.of(new SimpleGrantedAuthority("ROLE_" + rol));
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return cuentaActiva && "ACTIVO".equals(estado);
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return cuentaActiva && "ACTIVO".equals(estado);
	}

	@Override
	public boolean equals(Object other) {
		return this == other || (other instanceof TinyHatPrincipal principal
			&& userId.equals(principal.userId));
	}

	@Override
	public int hashCode() {
		return userId.hashCode();
	}
}
