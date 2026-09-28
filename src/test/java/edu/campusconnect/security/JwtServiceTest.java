package edu.campusconnect.security;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
class JwtServiceTest {
 @Test void signedTokenRoundTrip(){JwtService jwt=new JwtService("unit-test-secret-of-at-least-32-bytes-1234567890");UUID id=UUID.randomUUID();assertEquals(id,jwt.verify(jwt.issue(id)));}
 @Test void rejectTamperedToken(){JwtService jwt=new JwtService("unit-test-secret-of-at-least-32-bytes-1234567890");String token=jwt.issue(UUID.randomUUID());assertThrows(Exception.class,()->jwt.verify(token.substring(0,token.length()-5)+"XXXXX"));}
}
