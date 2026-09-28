package edu.campusconnect.repo;
import edu.campusconnect.model.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EmailTokenRepository extends JpaRepository<EmailToken, UUID> {java.util.Optional<EmailToken> findByTokenHash(String tokenHash);}
