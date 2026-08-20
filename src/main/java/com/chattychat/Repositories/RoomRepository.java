package com.chattychat.Repositories;

import com.chattychat.Entities.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {
    Optional<Room> findByName(String name);

    @Query("""
        SELECT DISTINCT r FROM Room r\s
        LEFT JOIN RoomMember rm ON rm.id.roomId = r.id AND rm.id.userId = :userId
        WHERE r.isPublic = true OR rm.id.userId IS NOT NULL
   \s""")
    List<Room> findAllAccessibleRooms(@Param("userId") UUID userId);

    boolean existsByName(String name);
}
