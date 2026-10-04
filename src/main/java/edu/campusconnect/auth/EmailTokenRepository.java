package edu.campusconnect.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailTokenRepository extends JpaRepository<EmailToken, UUID> {

    @Query("select t from EmailToken t join fetch t.student where t.tokenHash = :hash and t.purpose = :purpose")
    Optional<EmailToken> findByHash(@Param("hash") String hash, @Param("purpose") EmailToken.Purpose purpose);

    /** Retires outstanding tokens so that only the most recently issued link works. */
    @Modifying
    @Query("update EmailToken t set t.usedAt = :now where t.student.id = :studentId and t.purpose = :purpose and t.usedAt is null")
    int invalidateOutstanding(@Param("studentId") UUID studentId, @Param("purpose") EmailToken.Purpose purpose,
                              @Param("now") Instant now);

    @Modifying
    @Query("delete from EmailToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
