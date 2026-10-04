package edu.campusconnect.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.campusconnect.config.AppProperties;
import edu.campusconnect.security.JwtService;
import java.security.Principal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket at {@code /ws}. The browser authenticates in the STOMP CONNECT frame with its access token
 * ({@code Authorization: Bearer ...}); each student can only subscribe to their own {@code /user/queue/updates}.
 * Clients cannot send application messages — the channel is server-to-client only.
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    private final AppProperties props;
    private final JwtService jwt;
    private final ObjectMapper mapper;

    WebSocketConfig(AppProperties props, JwtService jwt, ObjectMapper mapper) {
        this.props = props;
        this.jwt = jwt;
        this.mapper = mapper;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(props.frontendUrl());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public boolean configureMessageConverters(List<MessageConverter> converters) {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(mapper);
        converters.add(converter);
        return false;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
                } else if (StompCommand.SEND.equals(accessor.getCommand())) {
                    throw new MessageDeliveryException("Clients may not send messages");
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    boolean allowed = destination != null
                            && (destination.equals(NotificationService.PRESENCE_TOPIC)
                            || destination.equals("/user" + NotificationService.USER_QUEUE));
                    if (!allowed || accessor.getUser() == null) {
                        throw new MessageDeliveryException("Subscription not allowed");
                    }
                }
                return message;
            }
        });
    }

    private Principal authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessageDeliveryException("Missing bearer token");
        }
        try {
            String studentId = jwt.verify(header.substring("Bearer ".length())).toString();
            return () -> studentId;
        } catch (RuntimeException e) {
            log.debug("Rejected WebSocket token: {}", e.getMessage());
            throw new MessageDeliveryException("Invalid bearer token");
        }
    }
}
