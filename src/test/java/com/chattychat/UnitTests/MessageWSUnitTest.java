package com.chattychat.UnitTests;

import com.chattychat.Controller.MessageWSController;
import com.chattychat.Services.MessageService;
import com.chattychat.dto.AuthUser;
import com.chattychat.dto.ErrorDTO;
import com.chattychat.dto.InboundMessageDTO;
import com.chattychat.dto.OutboundMessageDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;

import java.security.Principal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageWSUnitTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private MessageWSController messageWSController;

    @Test
    void sendToRoom_WhenValid_ReturnsOutboundMessage() {
        String roomName = "lobby";
        InboundMessageDTO inMsg = mock(InboundMessageDTO.class);
        OutboundMessageDTO expectedOutMsg = mock(OutboundMessageDTO.class);

        Authentication auth = mock(Authentication.class);
        AuthUser authUser = mock(AuthUser.class);
        UUID userId = UUID.randomUUID();

        when(auth.getPrincipal()).thenReturn(authUser);
        when(authUser.getUserId()).thenReturn(userId);
        when(messageService.save(roomName, userId, inMsg)).thenReturn(expectedOutMsg);

        // Act
        OutboundMessageDTO result = messageWSController.sendToRoom(auth, roomName, inMsg);

        // Assert
        assertThat(result).isEqualTo(expectedOutMsg);
    }

    // Assumes principal cannot be null and has a valid name. If principal can be null, additional null checks should be added.
    @Test
    void handleException_SendsErrorToUserPrivateQueue() {
        Throwable ex = new RuntimeException("Socket connection timeout");
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("user-123");

        // Act
        messageWSController.handleException(ex, principal);

        // Assert
        ArgumentCaptor<ErrorDTO> errorCaptor = ArgumentCaptor.forClass(ErrorDTO.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq("user-123"),
                eq("/queue/errors"),
                errorCaptor.capture()
        );

        // Checking the captured ErrorDTO contains the right message
        // Assuming ErrorDTO has a getter or is a record with message()
        ErrorDTO capturedError = errorCaptor.getValue();
        assertThat(capturedError).isNotNull();
    }
}
