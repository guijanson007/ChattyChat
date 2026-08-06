package com.chattychat.Controller;

import com.chattychat.Services.MessageService;
import com.chattychat.dto.OutboundMessageDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/rooms")
public class MessageRestController {
    private final MessageService messageService;

    public MessageRestController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/{room}/messages")
    public ResponseEntity<List<OutboundMessageDTO>> history(@PathVariable String room) {
        return ResponseEntity.ok(messageService.history(room));
    }
}