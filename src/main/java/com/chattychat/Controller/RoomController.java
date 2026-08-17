package com.chattychat.Controller;

import com.chattychat.Services.RoomService;
import com.chattychat.dto.RoomDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/rooms")
@Tag(name = "Room Management", description = "Endpoints for managing chat rooms")
public class RoomController {
    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @Operation(summary = "Get All Rooms", description = "Retrieve a list of all available rooms.")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved list of rooms",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = RoomDTO.class)))
            ),
            @ApiResponse(responseCode = "401", description = "Unauthorized access")
    })
    @GetMapping
    public ResponseEntity<List<RoomDTO>> getAllRooms() {
        return ResponseEntity.ok(roomService.getAllRooms());
    }

    @Operation(summary = "Create Room", description = "Create a new room.")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Room created successfully",
                    content = @Content(schema = @Schema(implementation = RoomDTO.class))
            ),
            @ApiResponse(responseCode = "400", description = "Invalid room data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized access")
    })
    @PostMapping
    public ResponseEntity<RoomDTO> createRoom(@RequestBody @Valid RoomDTO room) {
        RoomDTO createdRoom = roomService.createRoom(room);
        return ResponseEntity.created(URI.create("/v1/rooms/" + createdRoom.id()))
                .body(createdRoom);
    }
}
