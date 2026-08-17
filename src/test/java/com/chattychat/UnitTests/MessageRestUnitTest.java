package com.chattychat.UnitTests;

import com.chattychat.Controller.MessageRestController;
import com.chattychat.Services.MessageService;
import com.chattychat.dto.OutboundMessageDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageRestUnitTest {

    @Mock
    private MessageService messageService;

    @InjectMocks
    private MessageRestController messageRestController;

    @Test
    void history_WhenMessagesExist_Returns200AndList() {
        String roomId = "lobby-123";
        List<OutboundMessageDTO> expectedMessages = List.of(mock(OutboundMessageDTO.class));
        when(messageService.history(roomId)).thenReturn(expectedMessages);

        ResponseEntity<List<OutboundMessageDTO>> response = messageRestController.history(roomId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expectedMessages);
    }

    @Test
    void history_WhenMessagesListIsEmpty_Returns200AndEmptyList() {
        String roomId = "empty-room-456";
        when(messageService.history(roomId)).thenReturn(Collections.emptyList());

        ResponseEntity<List<OutboundMessageDTO>> response = messageRestController.history(roomId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }
}
