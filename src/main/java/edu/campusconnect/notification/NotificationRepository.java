package edu.campusconnect.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("select n from Notification n where n.studentId = :studentId order by n.createdAt desc")
    List<Notification> findLatest(@Param("studentId") UUID studentId, Pageable page);

    @Query("select count(n) from Notification n where n.studentId = :studentId and n.readAt is null")
    long countUnread(@Param("studentId") UUID studentId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.studentId = :studentId and n.readAt is null")
    int markAllRead(@Param("studentId") UUID studentId, @Param("now") Instant now);

    @Modifying
    @Query("delete from Notification n where n.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
