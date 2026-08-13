package com.chattychat.Services;

import com.chattychat.Entities.Room;
import com.chattychat.Repositories.RoomRepository;
import com.chattychat.dto.RoomDTO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoomService {
    private final RoomRepository roomRepository;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    public List<RoomDTO> getAllRooms() {
        return roomRepository.findAll()
                .stream()
                .map(room -> new RoomDTO(room.getId(), room.getName(), room.getCreatedAt()))
                .collect(Collectors.toList());
    }

    public RoomDTO createRoom(RoomDTO room) {
        return roomRepository.save(new Room(room.id(), room.name())).toDTO();
    }
}
