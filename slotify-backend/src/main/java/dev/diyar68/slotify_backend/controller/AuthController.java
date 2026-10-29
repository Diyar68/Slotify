package dev.diyar68.slotify_backend.controller;

import java.security.Principal;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;

import dev.diyar68.slotify_backend.dto.request.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.diyar68.slotify_backend.dto.request.RegisterRequest;
import dev.diyar68.slotify_backend.dto.response.ProviderResponse;
import dev.diyar68.slotify_backend.service.AuthService;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("api/auth/")
public class AuthController {
    private final AuthService authService;
    private final HttpSessionSecurityContextRepository contexts;
    private final SessionAuthenticationStrategy sessions;

    public AuthController(AuthService authService, HttpSessionSecurityContextRepository contexts,
            SessionAuthenticationStrategy sessions) {
        this.authService = authService;
        this.contexts = contexts;
        this.sessions = sessions;
    }

    @PostMapping("register")
    @ResponseStatus(HttpStatus.CREATED)
    public ProviderResponse registerProvider(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @GetMapping("csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }

    @PostMapping("login")
    public ProviderResponse login(@Valid @RequestBody LoginRequest login,
            HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = authService.login(login);
        ProviderResponse provider = authService.currentProvider(authentication.getName());
        sessions.onAuthentication(authentication, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return provider;
    }

    @GetMapping("me")
    public ProviderResponse me(Principal principal) {
        return authService.currentProvider(principal.getName());
    }

}
