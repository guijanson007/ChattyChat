package com.chattychat.Controller;

import com.chattychat.Services.MessageService;
import com.chattychat.dto.AuthUser;
import com.chattychat.dto.ErrorDTO;
import com.chattychat.dto.InboundMessageDTO;
import com.chattychat.dto.OutboundMessageDTO;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class MessageWSController {
    private final SimpMessagingTemplate messagingTemplate;
    private final MessageService messageService;

    public MessageWSController(SimpMessagingTemplate messagingTemplate, MessageService messageService) {
        this.messagingTemplate = messagingTemplate;
        this.messageService = messageService;
    }

    @MessageMapping("/chat.send/{room}")
    @SendTo("/topic/chat/{room}")
    public OutboundMessageDTO sendToRoom(
            Principal principal,
            @DestinationVariable("room") String roomName,
            @Payload InboundMessageDTO msg) {
        Authentication auth = (Authentication) principal;
        AuthUser user = (AuthUser) auth.getPrincipal();

        assert user != null;
        return messageService.save(roomName, user.getUserId(), msg);
    }

    @MessageExceptionHandler
    public void handleException(Throwable exception, Principal principal) {
        if (principal == null) {
            return;
        }

        // Send the error message only to the specific user's private error queue
        messagingTemplate.convertAndSendToUser(
                principal.getName(),
                "/queue/errors",
                new ErrorDTO(exception.getMessage())
        );
    }

}
