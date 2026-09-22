package br.com.gustavoakira.ticketing.core.identity.domain;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public record UserDetails(String name, String email, Set<Role> roles) {
    private static final Pattern EMAIL = Pattern.compile("(?U)^[^\\s@]+@[^\\s@]+$");

    public UserDetails {
        if (name == null || name.isBlank() || name.length() > 255) {
            throw new IllegalArgumentException("name must contain 1 to 255 characters");
        }
        email = normalizeEmail(email);
        if (roles == null || roles.isEmpty() || roles.stream().anyMatch(role -> role == null)) {
            throw new IllegalArgumentException("roles must contain at least one non-null role");
        }
        roles = Set.copyOf(roles);
    }

    public static String normalizeEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException("email is required");
        }
        var normalized = email.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > 254 || !EMAIL.matcher(normalized).matches()) {
            throw new IllegalArgumentException("email must be a valid address of up to 254 characters");
        }
        return normalized;
    }
}
