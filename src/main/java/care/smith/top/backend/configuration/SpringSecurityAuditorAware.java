package care.smith.top.backend.configuration;

import care.smith.top.backend.model.UserDao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration
public class SpringSecurityAuditorAware implements AuditorAware<String> {
  @Value("${spring.security.oauth2.enabled}")
  private Boolean oauth2Enabled;

  @Override
  public Optional<String> getCurrentAuditor() {
    if (!oauth2Enabled) return Optional.empty();

    return Optional.ofNullable(SecurityContextHolder.getContext())
        .map(SecurityContext::getAuthentication)
        .filter(Authentication::isAuthenticated)
        .map(Authentication::getPrincipal)
        .filter(p -> p instanceof UserDao)
        .map(UserDao.class::cast)
        .map(UserDao::getUsername);
  }
}
