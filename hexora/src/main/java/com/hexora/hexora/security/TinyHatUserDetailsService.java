package com.hexora.hexora.security;

import com.hexora.hexora.model.Cuenta;
import com.hexora.hexora.model.Usuario;
import com.hexora.hexora.repository.CuentaRepository;
import com.hexora.hexora.repository.UsuarioRepository;
import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TinyHatUserDetailsService implements UserDetailsService {

	private final UsuarioRepository usuarioRepository;
	private final CuentaRepository cuentaRepository;

	public TinyHatUserDetailsService(
		UsuarioRepository usuarioRepository,
		CuentaRepository cuentaRepository
	) {
		this.usuarioRepository = usuarioRepository;
		this.cuentaRepository = cuentaRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		String email = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
		Usuario usuario = usuarioRepository.findByEmailNormalizado(email)
			.orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
		Cuenta cuenta = cuentaRepository.findById(usuario.getCuentaId())
			.orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));

		return new TinyHatPrincipal(usuario, "ACTIVA".equals(cuenta.getEstado()));
	}
}
