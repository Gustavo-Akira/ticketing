package br.com.gustavoakira.ticketing.core.identity.presentation;
import br.com.gustavoakira.ticketing.core.identity.application.*;
import br.com.gustavoakira.ticketing.core.identity.port.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@ConditionalOnWebApplication
public class AuthController {
    private final RegisterUserUseCase register;
    private final LoginUseCase login;
    private final RefreshSessionUseCase refresh;
    private final LogoutUseCase logout;
    private final UserRepository users;
    private final AccessTokenIssuer issuer;
    public AuthController(RegisterUserUseCase register, LoginUseCase login, RefreshSessionUseCase refresh,
            LogoutUseCase logout, UserRepository users, AccessTokenIssuer issuer) {
        this.register = register; this.login = login; this.refresh = refresh;
        this.logout = logout; this.users = users; this.issuer = issuer;
    }
    @PostMapping("/register") public ResponseEntity<UserResult> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(register.execute(request.name(), request.email(), request.password()));
    }
    @PostMapping("/login") public ResponseEntity<TokenPair> login(@RequestBody LoginRequest request) {
        return tokens(login.execute(request.email(), request.password()));
    }
    @PostMapping("/refresh") public ResponseEntity<TokenPair> refresh(@RequestBody RefreshRequest request) {
        var result = refresh.execute(request.refreshToken());
        // Transaction already committed, including replay revocation.
        if (!result.accepted()) throw new InvalidCredentialsException();
        var user = users.findById(result.userId()).orElseThrow(InvalidCredentialsException::new);
        return tokens(new TokenPair(issuer.issue(user), "Bearer", 900, result.refreshToken(), result.refreshExpiresAt()));
    }
    @PostMapping("/logout") public ResponseEntity<Void> logout(@RequestBody RefreshRequest request) {
        logout.execute(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
    private ResponseEntity<TokenPair> tokens(TokenPair pair) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(pair);
    }
}
