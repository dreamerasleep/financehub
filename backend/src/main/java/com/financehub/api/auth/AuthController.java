package com.financehub.api.auth;

import com.financehub.application.auth.AuthService;
import com.financehub.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthDtos.AuthResponse register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        AuthService.AuthResult result = authService.register(request.email(), request.name(), request.password());
        return new AuthDtos.AuthResponse(result.token(), result.userId(), result.email(), result.name());
    }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        AuthService.AuthResult result = authService.login(request.email(), request.password());
        return new AuthDtos.AuthResponse(result.token(), result.userId(), result.email(), result.name());
    }

    @GetMapping("/me")
    public AuthDtos.MeResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return new AuthDtos.MeResponse(user.id(), user.email());
    }
}
