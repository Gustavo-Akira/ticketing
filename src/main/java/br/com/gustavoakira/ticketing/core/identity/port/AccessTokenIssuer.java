package br.com.gustavoakira.ticketing.core.identity.port;
import br.com.gustavoakira.ticketing.core.identity.domain.User;
public interface AccessTokenIssuer { String issue(User user); }
