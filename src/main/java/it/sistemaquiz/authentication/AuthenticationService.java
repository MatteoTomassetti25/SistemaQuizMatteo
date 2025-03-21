package it.sistemaquiz.authentication;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import it.sistemaquiz.entity.Utente;
import it.sistemaquiz.model.LoginUtente;
import it.sistemaquiz.model.RegisterUtente;
import it.sistemaquiz.repository.UtenteRepository;

@Service
public class AuthenticationService {
	
    private final UtenteRepository utenteRepository;
    
    private final PasswordEncoder passwordEncoder;
    
    private final AuthenticationManager authenticationManager;

    public AuthenticationService(
    	UtenteRepository userRepository,
        AuthenticationManager authenticationManager,
        PasswordEncoder passwordEncoder
    ) {
        this.authenticationManager = authenticationManager;
        this.utenteRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Utente signup(RegisterUtente input) {
    	Utente utente = new Utente();
    	utente.setNome(input.getNome());
    	utente.setCognome(input.getCognome());
    	utente.setMatricola(input.getMatricola());
    	utente.setPassword(passwordEncoder.encode(input.getPassword()));

        return utenteRepository.save(utente);
    }

    public Utente authenticate(LoginUtente input) {
        this.authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        input.getMatricola(),
                        input.getPassword()
                )
        );

        return utenteRepository.findByMatricola(input.getMatricola())
                .orElseThrow();
    }
}