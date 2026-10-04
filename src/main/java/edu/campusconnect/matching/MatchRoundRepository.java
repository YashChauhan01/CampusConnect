package edu.campusconnect.matching;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MatchRoundRepository extends JpaRepository<MatchRound, UUID> {

    @Query("select r from MatchRound r order by r.startedAt desc")
    List<MatchRound> recent(Pageable page);
}
