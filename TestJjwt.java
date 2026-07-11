import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;

public class TestJjwt {
    public static void main(String[] args) {
        try {
            String secretKey = "a_very_long_secret_key_for_testing_purposes_only_12345678901234567890";
            byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
            var key = Keys.hmacShaKeyFor(keyBytes);
            
            String token = Jwts.builder()
                .claim("passwordVersion", 0)
                .signWith(key)
                .compact();
            
            Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
                
            Object obj = claims.get("passwordVersion");
            System.out.println("Type: " + obj.getClass().getName());
            
            int tokenVersion = (Integer) obj;
            System.out.println("Value: " + tokenVersion);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
