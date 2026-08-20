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

    @Query("SELECT r FROM Room r JOIN RoomMember rm ON rm.id.roomId = r.id WHERE rm.id.userId = :userId")
    List<Room> findAllByUserId(@Param("userId") UUID userId);

    @Query("""
    SELECT r FROM Room r
    WHERE r.isPublic = true
      AND NOT EXISTS (
          SELECT 1 FROM RoomMember rm
          WHERE rm.id.roomId = r.id
            AND rm.id.userId = :userId
      )
    """)
    List<Room> findAllByIsPublicNotJoined(@Param("userId") UUID userId);

    boolean existsByName(String name);
}
