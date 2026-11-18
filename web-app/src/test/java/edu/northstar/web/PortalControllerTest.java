package edu.northstar.web;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import edu.northstar.contracts.*;
import io.grpc.Status;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PortalController.class)
@Import(WebSecurity.class)
class PortalControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean PortalApi api;

  private UsernamePasswordAuthenticationToken student() {
    var user =
        new PortalSession(
            AuthReply.newBuilder()
                .setName("Alex Morgan")
                .setUserId("2024-001")
                .setRole("STUDENT")
                .build());
    return new UsernamePasswordAuthenticationToken(
        user, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
  }

  @Test
  void anonymousUsersMustLoginAndWritesRequireCsrf() throws Exception {
    mvc.perform(get("/courses"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
    mvc.perform(post("/enroll").with(authentication(student())).param("courseId", "CS301"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(api);
  }

  @Test
  void dashboardStillShowsGradesWhenCoursesAreUnavailable() throws Exception {
    when(api.courses(any(), anyString(), anyString(), anyBoolean()))
        .thenThrow(Status.UNAVAILABLE.asRuntimeException());
    when(api.grades(any(), anyString())).thenReturn(GradeList.getDefaultInstance());
    mvc.perform(get("/").with(authentication(student())))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Courses are taking a short break.")))
        .andExpect(content().string(matchesPattern("(?s).*published\\s+grade\\s+records.*")))
        .andExpect(content().string(not(containsString("Whitelabel Error"))));
  }

  @Test
  void failedEnrollmentShowsAnErrorInsteadOfASuccessReceipt() throws Exception {
    when(api.enroll(any(), eq("CS301")))
        .thenThrow(
            Status.INVALID_ARGUMENT.withDescription("This course is full.").asRuntimeException());
    mvc.perform(
            post("/enroll").with(authentication(student())).with(csrf()).param("courseId", "CS301"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/enrollment"))
        .andExpect(flash().attribute("error", "This course is full."));
  }
}
