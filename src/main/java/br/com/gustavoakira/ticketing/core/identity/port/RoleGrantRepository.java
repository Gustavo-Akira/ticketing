package br.com.gustavoakira.ticketing.core.identity.port;
import java.util.UUID;
public interface RoleGrantRepository { boolean grantOrganizer(UUID userId); }
