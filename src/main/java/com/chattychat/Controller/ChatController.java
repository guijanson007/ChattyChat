package com.chattychat.Controller;

import com.chattychat.dto.ChatMessageDTO;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

@Controller
public class ChatController {
    @MessageMapping("/chat.send/{room}")
    @SendTo("/topic/chat/{room}")
    public ChatMessageDTO sendToRoom(@DestinationVariable String room, ChatMessageDTO msg) {
        msg = new ChatMessageDTO(msg.from(), msg.content(), LocalDateTime.now());
        return msg;
    }
}
