package com.chattychat.Controller;

import com.chattychat.Services.MessageService;
import com.chattychat.dto.OutboundMessageDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/rooms")
@Tag(name = "Message Management (REST)", description = "Endpoints for managing messages")
public class MessageRestController {
    private final MessageService messageService;

    public MessageRestController(MessageService messageService) {
        this.messageService = messageService;
    }

    @Operation(summary = "Get Message History", description = "Retrieve the message history for a specific room.")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved message history",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = OutboundMessageDTO.class)))
            ),
            @ApiResponse(responseCode = "404", description = "Room not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized access")
    })
    @GetMapping("/{room}/messages")
    public ResponseEntity<List<OutboundMessageDTO>> history(@PathVariable String room) {
        List<OutboundMessageDTO> messages = messageService.history(room);
        return messages == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(messages);
    }
}
