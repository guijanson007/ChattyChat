package com.chattychat.UnitTests;

import com.chattychat.Controller.RoomController;
import com.chattychat.Services.RoomService;
import com.chattychat.dto.RoomDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomUnitTest {

    @Mock
    private RoomService roomService;

    @InjectMocks
    private RoomController roomController;

    /*
    @Test
    void getAllRooms_Returns200AndListOfRooms() {
        List<RoomDTO> expectedRooms = List.of(mock(RoomDTO.class));
        when(roomService.getAllRooms()).thenReturn(expectedRooms);

        ResponseEntity<List<RoomDTO>> response = roomController.getAllRooms();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expectedRooms);
    }*/

    /*
    @Test
    void createRoom_Returns201AndCreatedRoom() {
        RoomDTO inputRoom = mock(RoomDTO.class);
        RoomDTO createdRoom = mock(RoomDTO.class);

        when(roomService.createRoom(inputRoom)).thenReturn(createdRoom);

        ResponseEntity<RoomDTO> response = roomController.createRoom(inputRoom);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isEqualTo(URI.create("/v1/rooms/" + createdRoom.id()));
        assertThat(response.getBody()).isEqualTo(createdRoom);
    }
    */
}
