package edu.northstar.web;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.northstar.contracts.*;
import io.grpc.Status;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest({PortalController.class, PortalViewController.class})
@Import(WebSecurity.class)
class PortalControllerTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockitoBean PortalApi api;

  private static final AuthReply STUDENT =
      AuthReply.newBuilder()
          .setName("Alex Morgan")
          .setUserId("2024-001")
          .setRole("STUDENT")
          .setAccessToken("test-access-token")
          .setRefreshToken("test-refresh-token")
          .build();

  private UsernamePasswordAuthenticationToken student() {
    return new UsernamePasswordAuthenticationToken(
        new PortalSession(STUDENT), null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
  }

  @Test
  void pageRoutesRedirectAndApiRoutesReturnAuthErrors() throws Exception {
    mvc.perform(get("/courses"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
    mvc.perform(get("/courses").with(authentication(student())))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/index.html"));
    mvc.perform(get("/api/courses"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Please sign in again."));
    mvc.perform(post("/api/enroll").with(authentication(student())).param("courseId", "CS301"))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/faculty/discard").with(authentication(student())).with(csrf()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").isString());
    verifyNoInteractions(api);
  }

  @Test
  void gradeDataStaysAvailableWhenCoursesAreUnavailable() throws Exception {
    when(api.courses(any(), anyString(), anyString(), anyBoolean()))
        .thenThrow(Status.UNAVAILABLE.asRuntimeException());
    when(api.grades(any(), eq("")))
        .thenReturn(
            GradeList.newBuilder()
                .addGrades(Grade.newBuilder().setCourseCode("CS 301").setValue("1.25"))
                .build());
    mvc.perform(get("/api/courses").with(authentication(student())))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.message").value(containsString("temporarily unavailable")));
    mvc.perform(get("/api/grades").with(authentication(student())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].courseCode").value("CS 301"))
        .andExpect(jsonPath("$[0].value").value("1.25"));
  }

  @Test
  void failedEnrollmentReturnsAClientErrorInsteadOfAReceipt() throws Exception {
    when(api.enroll(any(), eq("CS301")))
        .thenThrow(
            Status.INVALID_ARGUMENT.withDescription("This course is full.").asRuntimeException());
    mvc.perform(
            post("/api/enroll")
                .with(authentication(student()))
                .with(csrf())
                .param("courseId", "CS301"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("This course is full."));
  }

  @Test
  void browserCsrfTokensFollowTheSessionAndJwtStaysOnTheServer() throws Exception {
    when(api.login("alex@example.edu", "password")).thenReturn(STUDENT);
    var anonymous =
        mvc.perform(get("/api/session"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.user").doesNotExist())
            .andReturn();
    var firstSession = (MockHttpSession) anonymous.getRequest().getSession(false);
    var anonymousToken = csrfToken(anonymous);

    var login =
        mvc.perform(
                post("/api/login")
                    .session(firstSession)
                    .header("X-CSRF-TOKEN", anonymousToken)
                    .param("email", "alex@example.edu")
                    .param("password", "password"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Alex Morgan"))
            .andExpect(jsonPath("$.role").value("STUDENT"))
            .andExpect(content().string(not(containsString("test-access-token"))))
            .andReturn();
    var authenticated = (MockHttpSession) login.getRequest().getSession(false);
    assert authenticated != null && !authenticated.getId().equals(firstSession.getId());

    var active =
        mvc.perform(get("/api/session").session(authenticated))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.id").value("2024-001"))
            .andExpect(content().string(not(containsString("test-refresh-token"))))
            .andReturn();

    mvc.perform(post("/api/signout").session(authenticated).header("X-CSRF-TOKEN", anonymousToken))
        .andExpect(status().isForbidden());
    doThrow(Status.UNAVAILABLE.asRuntimeException()).when(api).logout(any());
    mvc.perform(
            post("/api/signout").session(authenticated).header("X-CSRF-TOKEN", csrfToken(active)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revocationPending").value(true));
    org.junit.jupiter.api.Assertions.assertTrue(authenticated.isInvalid());
  }

  private String csrfToken(MvcResult result) throws Exception {
    var csrf = json.readTree(result.getResponse().getContentAsString()).path("csrf");
    org.junit.jupiter.api.Assertions.assertEquals("X-CSRF-TOKEN", csrf.path("headerName").asText());
    return csrf.path("token").asText();
  }
}
