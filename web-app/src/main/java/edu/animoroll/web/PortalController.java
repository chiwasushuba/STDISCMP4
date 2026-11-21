package edu.animoroll.web;

import edu.animoroll.contracts.GradeUpload;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class PortalController {
  public static final String TERM = "2026–2027 · Semester 1";
  private static final String PENDING_GRADES = "pendingGrades";
  private final PortalApi api;
  private final HttpSessionSecurityContextRepository sessions =
      new HttpSessionSecurityContextRepository();

  private record PendingGrades(String id, GradeUpload upload) {}

  public PortalController(PortalApi api) {
    this.api = api;
  }

  @GetMapping("/session")
  ResponseEntity<PortalData.Session> session(
      @AuthenticationPrincipal PortalSession user, CsrfToken csrf) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            new PortalData.Session(
                user == null ? null : PortalData.User.from(user),
                TERM,
                new PortalData.Csrf(csrf.getHeaderName(), csrf.getToken())));
  }

  @PostMapping("/login")
  PortalData.User login(
      @RequestParam String email,
      @RequestParam String password,
      HttpServletRequest request,
      HttpServletResponse response) {
    var user = new PortalSession(api.login(email, password));
    HttpSession previous = request.getSession(false);
    if (previous != null) previous.invalidate();
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        new UsernamePasswordAuthenticationToken(
            user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))));
    SecurityContextHolder.setContext(context);
    sessions.saveContext(context, request, response);
    return PortalData.User.from(user);
  }

  @PostMapping("/signout")
  PortalData.Signout signout(
      @AuthenticationPrincipal PortalSession user,
      Authentication authentication,
      HttpServletRequest request,
      HttpServletResponse response) {
    boolean revocationPending = false;
    try {
      api.logout(user);
    } catch (StatusRuntimeException exception) {
      revocationPending = true;
    } finally {
      new SecurityContextLogoutHandler().logout(request, response, authentication);
    }
    return new PortalData.Signout(revocationPending);
  }

  @GetMapping("/courses")
  List<PortalData.CourseItem> courses(
      @AuthenticationPrincipal PortalSession user,
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String department) {
    return courseItems(user, search, department, false);
  }

  @GetMapping("/enrollment")
  List<PortalData.CourseItem> enrollment(@AuthenticationPrincipal PortalSession user) {
    return courseItems(user, "", "", true);
  }

  @PostMapping("/enroll")
  PortalData.Message enroll(
      @AuthenticationPrincipal PortalSession user, @RequestParam String courseId) {
    return new PortalData.Message(api.enroll(user, courseId).getMessage());
  }

  @GetMapping("/grades")
  List<PortalData.GradeItem> grades(@AuthenticationPrincipal PortalSession user) {
    return api.grades(user, "").getGradesList().stream().map(PortalData.GradeItem::from).toList();
  }

  @GetMapping("/faculty")
  PortalData.Faculty faculty(
      @AuthenticationPrincipal PortalSession user,
      @RequestParam(defaultValue = "") String code,
      @RequestParam(defaultValue = "") String term,
      HttpSession session) {
    var classes =
        api.classes(user).getClassesList().stream().map(PortalData.ClassItem::from).toList();
    if (classes.isEmpty()) return new PortalData.Faculty(classes, null, List.of(), null);

    var selected =
        classes.stream()
            .filter(item -> item.courseCode().equals(code) && item.term().equals(term))
            .findFirst()
            .orElse(classes.getFirst());
    var roster =
        api.roster(user, selected.courseCode(), selected.term()).getGradesList().stream()
            .map(PortalData.GradeItem::from)
            .toList();
    PortalData.Preview preview = null;
    if (session.getAttribute(PENDING_GRADES) instanceof PendingGrades pending
        && pending.upload().getCourseCode().equals(selected.courseCode())
        && pending.upload().getTerm().equals(selected.term())) {
      preview =
          new PortalData.Preview(
              pending.id(),
              pending.upload().getRowsList().stream()
                  .map(row -> new PortalData.PreviewRow(row.getStudentId(), row.getValue()))
                  .toList());
    }
    return new PortalData.Faculty(classes, selected, roster, preview);
  }

  @PostMapping(value = "/faculty/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void preview(
      @AuthenticationPrincipal PortalSession user,
      @RequestParam MultipartFile file,
      @RequestParam String code,
      @RequestParam String term,
      HttpSession session) {
    synchronized (user) {
      session.removeAttribute(PENDING_GRADES);
      if (file.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a non-empty CSV file.");
      }
      GradeUpload upload;
      try {
        upload = CsvGrades.parse(new String(file.getBytes(), StandardCharsets.UTF_8), code, term);
      } catch (IllegalArgumentException exception) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
      } catch (IOException exception) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "The CSV file could not be read. Choose it again.", exception);
      }
      api.roster(user, code, term);
      session.setAttribute(PENDING_GRADES, new PendingGrades(UUID.randomUUID().toString(), upload));
    }
  }

  @PostMapping("/faculty/publish")
  PortalData.Message publish(
      @AuthenticationPrincipal PortalSession user,
      @RequestParam(defaultValue = "") String previewId,
      HttpSession session) {
    synchronized (user) {
      var pending = matchingPreview(session, previewId);
      var receipt = api.upload(user, pending.upload());
      session.removeAttribute(PENDING_GRADES);
      return new PortalData.Message(receipt.getMessage());
    }
  }

  @PostMapping("/faculty/discard")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void discard(
      @AuthenticationPrincipal PortalSession user,
      @RequestParam(defaultValue = "") String previewId,
      HttpSession session) {
    synchronized (user) {
      matchingPreview(session, previewId);
      session.removeAttribute(PENDING_GRADES);
    }
  }

  @GetMapping("/faculty/template")
  ResponseEntity<String> template(
      @AuthenticationPrincipal PortalSession user,
      @RequestParam String code,
      @RequestParam String term) {
    var roster = api.roster(user, code, term).getGradesList();
    StringBuilder csv = new StringBuilder("student_id,grade\n");
    roster.forEach(
        grade ->
            csv.append(grade.getStudentId()).append(',').append(grade.getValue()).append('\n'));
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=grades.csv")
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(csv.toString());
  }

  private List<PortalData.CourseItem> courseItems(
      PortalSession user, String search, String department, boolean enrolledOnly) {
    return api.courses(user, search, department, enrolledOnly).getCoursesList().stream()
        .map(PortalData.CourseItem::from)
        .toList();
  }

  private PendingGrades matchingPreview(HttpSession session, String previewId) {
    if (session.getAttribute(PENDING_GRADES) instanceof PendingGrades pending
        && pending.id().equals(previewId)) {
      return pending;
    }
    throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "This preview is no longer current. Review the latest file before publishing.");
  }

  @ExceptionHandler(StatusRuntimeException.class)
  ResponseEntity<PortalData.Message> rpcError(
      StatusRuntimeException exception, HttpServletRequest request, HttpServletResponse response) {
    HttpStatus status =
        switch (exception.getStatus().getCode()) {
          case INVALID_ARGUMENT, OUT_OF_RANGE -> HttpStatus.BAD_REQUEST;
          case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
          case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
          case NOT_FOUND -> HttpStatus.NOT_FOUND;
          case ALREADY_EXISTS, ABORTED, FAILED_PRECONDITION -> HttpStatus.CONFLICT;
          default -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    if (status == HttpStatus.UNAUTHORIZED
        && !request.getRequestURI().equals(request.getContextPath() + "/api/login")) {
      // A rejected refresh no longer authorizes new requests. A service outage does not log out.
      var logout = new SecurityContextLogoutHandler();
      logout.setInvalidateHttpSession(false);
      logout.logout(request, response, SecurityContextHolder.getContext().getAuthentication());
    }
    String message =
        status == HttpStatus.SERVICE_UNAVAILABLE
            ? "This feature is temporarily unavailable. Please try again shortly."
            : Objects.requireNonNullElse(
                exception.getStatus().getDescription(), "The request could not be completed.");
    return error(status, message);
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<PortalData.Message> statusError(ResponseStatusException exception) {
    return error(exception.getStatusCode(), exception.getReason());
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<PortalData.Message> oversizedUpload() {
    return error(HttpStatus.PAYLOAD_TOO_LARGE, "Choose a CSV file no larger than 256 KB.");
  }

  private ResponseEntity<PortalData.Message> error(HttpStatusCode status, String message) {
    return ResponseEntity.status(status).body(new PortalData.Message(message));
  }
}
