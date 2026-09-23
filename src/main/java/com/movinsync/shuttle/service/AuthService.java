package com.movinsync.shuttle.service;

import com.movinsync.shuttle.dto.AuthResponse;
import com.movinsync.shuttle.dto.LoginRequest;
import com.movinsync.shuttle.dto.RegisterRequest;
import com.movinsync.shuttle.entity.User;
import com.movinsync.shuttle.exception.DuplicateBookingException;
import com.movinsync.shuttle.repository.UserRepository;
import com.movinsync.shuttle.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.*;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateBookingException(
                    "Email already registered: " + request.getEmail());
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(User.Role.EMPLOYEE)
                .build();

        userRepository.save(user);
        log.info("New user registered: email={}", user.getEmail());

        UserDetails details = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtUtil.generateToken(details);

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow();

        UserDetails details = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtUtil.generateToken(details);

        log.info("User logged in: email={}", user.getEmail());

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
}
