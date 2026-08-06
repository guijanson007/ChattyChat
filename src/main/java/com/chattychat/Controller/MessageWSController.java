package com.chattychat.Controller;

import com.chattychat.Services.MessageService;
import com.chattychat.dto.InboundMessageDTO;
import com.chattychat.dto.OutboundMessageDTO;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class MessageWSController {
    private final MessageService messageService;

    public MessageWSController(MessageService messageService) {
        this.messageService = messageService;
    }

    @MessageMapping("/chat.send/{room}")
    @SendTo("/topic/chat/{room}")
    public OutboundMessageDTO sendToRoom(@DestinationVariable("room") String roomName, InboundMessageDTO msg) {
        return messageService.save(roomName, msg);
    }

}
