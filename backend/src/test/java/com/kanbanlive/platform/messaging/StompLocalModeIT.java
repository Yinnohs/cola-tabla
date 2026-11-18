package com.kanbanlive.platform.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.kanbanlive.testsupport.PostgresIT;
import com.kanbanlive.testsupport.StompTestClient;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.TestPropertySource;

/** Con kanban.security.enabled=false (solo local) el CONNECT no exige token y se usa el usuario de desarrollo. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "kanban.security.enabled=false")
class StompLocalModeIT extends PostgresIT {

    @LocalServerPort
    int port;
    @Autowired
    SimpMessagingTemplate broker;

    @Test
    void connectsWithoutTokenAndSubscribesToAnyBoard() throws Exception {
        UUID board = UUID.randomUUID();
        try (StompTestClient c = StompTestClient.connect(port, null)) {
            assertThat(c.awaitConnected()).isTrue();
            c.subscribe("/topic/boards." + board);
            Thread.sleep(300);
            broker.convertAndSend("/topic/boards." + board, "local");

            String m = c.nextMessage(3000);
            assertThat(m).as("errors=" + c.errors).isEqualTo("local");
        }
    }
}
