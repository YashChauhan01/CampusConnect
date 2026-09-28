package edu.campusconnect.repo;
import edu.campusconnect.model.*;import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface RefreshTokenRepository extends JpaRepository<RefreshToken,UUID> {java.util.Optional<RefreshToken> findByTokenHash(String tokenHash);}
