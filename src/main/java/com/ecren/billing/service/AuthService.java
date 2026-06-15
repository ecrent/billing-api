package com.ecren.billing.service;

import com.ecren.billing.domain.Tenant;
import com.ecren.billing.domain.User;
import com.ecren.billing.domain.enums.UserRole;
import com.ecren.billing.dto.request.LoginRequest;
import com.ecren.billing.dto.request.RegisterRequest;
import com.ecren.billing.dto.response.AuthResponse;
import com.ecren.billing.exception.ConflictException;
import com.ecren.billing.repository.TenantRepository;
import com.ecren.billing.repository.UserRepository;
import com.ecren.billing.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, TenantRepository tenantRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already registered");
        }

        Tenant tenant = new Tenant();
        tenant.setName(request.name());
        tenant.setEmail(request.email());
        tenantRepository.save(tenant);

        User user = new User();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.USER);
        user.setTenantId(tenant.getId());
        userRepository.save(user);

        return new AuthResponse(jwtService.generate(
                user.getId(), user.getEmail(), user.getRole().name(), tenant.getId()));
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        return new AuthResponse(jwtService.generate(
                user.getId(), user.getEmail(), user.getRole().name(), user.getTenantId()));
    }
}
