package com.movinsync.shuttle.service;

import com.movinsync.shuttle.dto.AuthResponse;
import com.movinsync.shuttle.dto.RegisterRequest;
import com.movinsync.shuttle.entity.User;
import com.movinsync.shuttle.repository.UserRepository;
import com.movinsync.shuttle.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private org.springframework.security.authentication.AuthenticationManager authenticationManager;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserDetailsService userDetailsService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode("StrongPass123")).thenReturn("hashed-password");
    }

    @Test
    @DisplayName("Should always register public users as employees")
    void register_shouldNotAllowAdminRoleEscalation() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Admin User");
        request.setEmail("admin@company.com");
        request.setPassword("StrongPass123");

        when(userRepository.existsByEmail("admin@company.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            savedUser.setId(99L);
            return savedUser;
        });

        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername("admin@company.com")
                .password("hashed-password")
                .authorities("ROLE_EMPLOYEE")
                .build();

        when(userDetailsService.loadUserByUsername("admin@company.com")).thenReturn(userDetails);
        when(jwtUtil.generateToken(userDetails)).thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("jwt-token", response.getToken());
        assertEquals(User.Role.EMPLOYEE.name(), response.getRole());
        verify(userRepository).save(argThat(user -> user.getRole() == User.Role.EMPLOYEE));
    }
}