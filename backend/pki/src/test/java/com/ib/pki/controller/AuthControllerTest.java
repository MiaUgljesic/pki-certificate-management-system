package com.ib.pki.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ib.pki.model.User;
import com.ib.pki.model.enums.UserRole;
import com.ib.pki.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        userRepository.save(User.builder()
                .email("admin@example.com")
                .passwordHash(passwordEncoder.encode("StrongPass123!"))
                .firstName("Admin")
                .lastName("User")
                .role(UserRole.ADMIN)
                .active(true)
                .build());

        userRepository.save(User.builder()
                .email("user@example.com")
                .passwordHash(passwordEncoder.encode("UserPass123!"))
                .firstName("Regular")
                .lastName("User")
                .role(UserRole.USER)
                .active(true)
                .build());
    }

    @Test
    void loginShouldAuthenticateAdminWhenCredentialsAreValid() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "admin@example.com",
                                  "password": "StrongPass123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void loginShouldAuthenticateUserWhenCredentialsAreValid() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "UserPass123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void loginShouldReturnBadRequestWhenRequestPayloadIsInvalid() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "not-an-email",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

        @Test
        void refreshShouldRotateTokenAndRejectReuse() throws Exception {
      String loginResponse = mockMvc.perform(post("/api/auth/login")
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
            {
              "email": "admin@example.com",
              "password": "StrongPass123!"
            }
            """))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();

      String refreshToken = objectMapper.readTree(loginResponse).get("refreshToken").asText();

      String refreshResponse = mockMvc.perform(post("/api/auth/refresh")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(new RefreshPayload(refreshToken))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
        .andExpect(jsonPath("$.refreshToken", not(blankOrNullString())))
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andReturn()
        .getResponse()
        .getContentAsString();

      String rotatedRefreshToken = objectMapper.readTree(refreshResponse).get("refreshToken").asText();
      org.junit.jupiter.api.Assertions.assertNotEquals(refreshToken, rotatedRefreshToken);

      mockMvc.perform(post("/api/auth/refresh")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(new RefreshPayload(refreshToken))))
        .andExpect(status().isUnauthorized());
        }

        @Test
        void expiredAccessTokenShouldReturnUnauthorized() throws Exception {
      SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
      Date now = new Date();
      String expiredToken = Jwts.builder()
        .subject("admin@example.com")
        .issuedAt(new Date(now.getTime() - 3600_000))
        .expiration(new Date(now.getTime() - 1000))
        .signWith(key)
        .compact();

      mockMvc.perform(post("/api/admin/register-ca")
          .contentType(MediaType.APPLICATION_JSON)
          .header("Authorization", "Bearer " + expiredToken)
          .content("""
            {
              "organizationName": "Org One",
              "userEmail": "ca@example.com",
              "firstName": "CA",
              "lastName": "User"
            }
            """))
        .andExpect(status().isUnauthorized());
        }

        private record RefreshPayload(String refreshToken) {
        }
}
